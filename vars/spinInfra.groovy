#!groovy
import groovy.json.JsonSlurperClassic
import uk.gov.hmcts.contino.Environment
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.TerraformTagMap
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.pipeline.AKSSubscriptions
import uk.gov.hmcts.contino.RepositoryUrl

def call(product, environment, tfPlanOnly, subscription) {
  call(product, null, environment, tfPlanOnly, subscription)
}

def call(product, component, environment, tfPlanOnly, subscription) {
  call(product, component, environment, tfPlanOnly, subscription, "")
}

def call(product, component, environment, tfPlanOnly, subscription, deploymentTarget) {
  call(
    product: product,
    component: component,
    environment: environment,
    tfPlanOnly: tfPlanOnly,
    subscription: subscription,
    deploymentTarget: deploymentTarget,
  )
}

def call(Map<String, ?> params) {
  def config = [
        productName     : params.component ? "$params.product-$params.component" : params.product,
        deploymentNamespace : new ProjectBranch(env.BRANCH_NAME).deploymentNamespace(),
  ] << params

  def productName = config.component
  def changeUrl = config.changeUrl
  def environmentDeploymentTarget = params.environment
  def teamName

  MetricsPublisher metricsPublisher = new MetricsPublisher(
    this, currentBuild, config.product, config.component
  )

  onPreview {
    changeUrl = env.CHANGE_URL
  }

  if (env.SUBSCRIPTION_NAME == null) {
    throw new Exception("There is no SUBSCRIPTION_NAME environment variable, are you running inside a withSubscription block?")
  }

  approvedTerraformInfrastructure(config.environment, config.product, metricsPublisher) {
    stateStoreInit(config.environment, config.subscription, config.deploymentTarget)

    lock("${config.productName}-${environmentDeploymentTarget}") {
      stageWithAgent("Plan ${config.productName} in ${environmentDeploymentTarget}", config.product) {

        teamName = env.TEAM_NAME
        def contactSlackChannel = env.CONTACT_SLACK_CHANNEL

        def builtFrom = env.GIT_URL ?: 'unknown'

        def tags = [environment: Environment.toTagName(config.environment), managedBy: teamName, builtFrom: builtFrom, contactSlackChannel: contactSlackChannel, application: env.TEAM_APPLICATION_TAG, businessArea: env.BUSINESS_AREA_TAG]

        if (Environment.toTagName(config.environment) == "sandbox") {
          tags = tags + [expiresAfter: config.expires]
        }

        if (changeUrl && changeUrl != "null" && changeUrl != "") {
          tags = tags + [changeUrl: changeUrl]
        }

        String pipelineTags = new TerraformTagMap(tags).toString()

        log.info "Building with following input parameters: common_tags='$pipelineTags'; product='$config.product'; component='$config.component'; deploymentNamespace='$config.deploymentNamespace'; environment='$config.environment'; subscription='$config.subscription'; tfPlanOnly='$config.tfPlanOnly'"

        if (env.STORE_rg_name_template != null &&
          env.STORE_sa_name_template != null &&
          env.STORE_sa_container_name_template != null) {
          log.warning("Using following stateStore={" +
            "'rg_name': '${env.STORE_rg_name_template}-${config.subscription}', " +
            "'sa_name': '${env.STORE_sa_name_template}${config.subscription}', " +
            "'sa_container_name': '${env.STORE_sa_container_name_template}${environmentDeploymentTarget}'}")
        } else
          throw new Exception("State store name details not found in environment variables?")

        sh 'env|grep "TF_VAR\\|AZURE\\|ARM\\|STORE" | grep -v ARM_ACCESS_KEY'

        try {
          sh "tfenv install"
        } catch (ignored) {
          echo "No .terraform-version file present, falling back to last terraform version pre tfenv"
          sh "tfenv use 0.11.7"
        }

        sh "terraform --version"

        sh """
          terraform init -reconfigure \
            -backend-config "storage_account_name=${env.STORE_sa_name_template}${config.subscription}" \
            -backend-config "container_name=${env.STORE_sa_container_name_template}${environmentDeploymentTarget}" \
            -backend-config "resource_group_name=${env.STORE_rg_name_template}-${config.subscription}" \
            -backend-config "key=${config.productName}/${environmentDeploymentTarget}/terraform.tfstate"
        """

        warnAboutOldTfAzureProvider()

        env.TF_VAR_subscription = config.subscription
        env.TF_VAR_component = config.component

        def aksSubscription = new AKSSubscriptions(this).getAKSSubscriptionByEnvName(config.environment)

        if (aksSubscription != null) {
          env.TF_VAR_aks_subscription_id = aksSubscription.id
        }

        sh 'env|grep "TF_VAR\\|AZURE\\|ARM\\|STORE" | grep -v ARM_ACCESS_KEY'

        sh "terraform get -update=true"
        sh "terraform plan -out tfplan -var 'common_tags=${pipelineTags}' -var 'env=${config.environment}' -var 'product=${config.product}'" +
          (fileExists("${config.environment}.tfvars") ? " -var-file=${config.environment}.tfvars" : "")

        onPR {
          String repositoryShortUrl = new RepositoryUrl().getShortWithoutOrgOrSuffix(env.CHANGE_URL)
          def credentialsId = env.GIT_CREDENTIALS_ID
          withCredentials([usernamePassword(credentialsId: credentialsId, passwordVariable: 'GITHUB_TOKEN', usernameVariable: 'APP_ID')]) {
            sh """
              tfcmt --owner hmcts \
                --repo ${repositoryShortUrl} \
                --pr ${env.CHANGE_ID} \
                plan -patch -- \
                terraform show tfplan
            """
          }
        }
      }
      if (!config.tfPlanOnly) {
        stageWithAgent("Apply ${config.productName} in ${environmentDeploymentTarget}", config.product) {
          sh "terraform apply -auto-approve tfplan"
          parseResult = null
          try {
            result = sh(script: "terraform output -json", returnStdout: true).trim()
            parseResult = new JsonSlurperClassic().parseText(result)
            log.info("returning parsed JSON terraform output: ${parseResult}")
          } catch (err) {
            log.info("terraform output command failed! ${err} Assuming there was no result...")
          }
          return parseResult
        }
      } else
        log.warning "Skipping apply due to tfPlanOnly flag set"
    }
  }
}
