#!groovy
import groovy.json.JsonSlurperClassic
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.TerraformTagMap
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.pipeline.AKSSubscriptions

def call(productName, environment, tfPlanOnly, subscription) {
  call(productName, null, environment, tfPlanOnly, subscription)
}

def call(product, component, environment, tfPlanOnly, subscription) {
  call(product, component, environment, tfPlanOnly, subscription, "")
}

def call(product, component, environment, tfPlanOnly, subscription, deploymentTarget) {
  def branch = new ProjectBranch(env.BRANCH_NAME)

  def deploymentNamespace = branch.deploymentNamespace()
  def productName = component ? "$product-$component" : product
  def changeUrl = ""
  def environmentDeploymentTarget = "$environment"
  def teamName
  def pipelineTags

  metricsPublisher = new MetricsPublisher(
    this, currentBuild, product, component, subscription
  )

  onPreview {
    changeUrl = env.CHANGE_URL
  }

  if (env.SUBSCRIPTION_NAME == null) {
    throw new Exception("There is no SUBSCRIPTION_NAME environment variable, are you running inside a withSubscription block?")
  }

  approvedTerraformInfrastructure(environment, product, metricsPublisher) {
    stateStoreInit(environment, subscription, deploymentTarget)

    lock("${productName}-${environmentDeploymentTarget}") {
      stageWithAgent("Plan ${productName} in ${environmentDeploymentTarget}", product) {

        teamName = env.TEAM_NAME
        def contactSlackChannel = env.CONTACT_SLACK_CHANNEL

        def builtFrom = env.GIT_URL ?: 'unknown'
        pipelineTags = new TerraformTagMap([environment: environment, changeUrl: changeUrl, managedBy: teamName, BuiltFrom: builtFrom, contactSlackChannel: contactSlackChannel, application: env.TEAM_APPLICATION_TAG ]).toString()
        log.info "Building with following input parameters: common_tags='$pipelineTags'; product='$product'; component='$component'; deploymentNamespace='$deploymentNamespace'; environment='$environment'; subscription='$subscription'; tfPlanOnly='$tfPlanOnly'"

        if (env.STORE_rg_name_template != null &&
          env.STORE_sa_name_template != null &&
          env.STORE_sa_container_name_template != null) {
          log.warning("Using following stateStore={" +
            "'rg_name': '${env.STORE_rg_name_template}-${subscription}', " +
            "'sa_name': '${env.STORE_sa_name_template}${subscription}', " +
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

        warnAboutOldTfAzureProvider()

        sh """
          terraform init -reconfigure \
            -backend-config "storage_account_name=${env.STORE_sa_name_template}${subscription}" \
            -backend-config "container_name=${env.STORE_sa_container_name_template}${environmentDeploymentTarget}" \
            -backend-config "resource_group_name=${env.STORE_rg_name_template}-${subscription}" \
            -backend-config "key=${productName}/${environmentDeploymentTarget}/terraform.tfstate"
        """

        env.TF_VAR_ilbIp = 'TODO remove after some time'
        env.TF_VAR_deployment_namespace = deploymentNamespace
        env.TF_VAR_subscription = subscription
        env.TF_VAR_component = component
        if (environment ==~ /^idam-packer-.*/ ) {
          environmentName = environment.replace("idam-packer-","")
        } else if (environment ==~ /^idam-.*/ ) {
          environmentName = environment.replace("idam-","")
        } else {
          environmentName = environment
        }
        env.TF_VAR_aks_subscription_id = new AKSSubscriptions(this).getAKSSubscriptionByEnvName(environmentName).id

        sh 'env|grep "TF_VAR\\|AZURE\\|ARM\\|STORE" | grep -v ARM_ACCESS_KEY'

        // Do not attempt to import during PR build
        if (!tfPlanOnly) {
          importTerraformModules(subscription, environment, product, pipelineTags)
        }

        sh "terraform get -update=true"
        sh "terraform plan -out tfplan -var 'common_tags=${pipelineTags}' -var 'env=${environment}' -var 'product=${product}'" +
          (fileExists("${environment}.tfvars") ? " -var-file=${environment}.tfvars" : "")
      }
      if (!tfPlanOnly) {
        stageWithAgent("Apply ${productName} in ${environmentDeploymentTarget}", product) {
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
