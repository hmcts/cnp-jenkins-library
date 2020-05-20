#!groovy
import groovy.json.JsonSlurperClassic
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.TerraformTagMap
import uk.gov.hmcts.pipeline.TeamConfig
import uk.gov.hmcts.contino.MetricsPublisher

def call(productName, environment, planOnly, subscription) {
  call(productName, null, environment, planOnly, subscription)
}

def call(product, component, environment, planOnly, subscription) {
  call(product, component, environment, planOnly, subscription, "")
}

def call(product, component, environment, planOnly, subscription, deploymentTarget) {
  def branch = new ProjectBranch(env.BRANCH_NAME)

  def deploymentNamespace = branch.deploymentNamespace()
  def productName = component ? "$product-$component" : product
  def changeUrl = ""
  def environmentDeploymentTarget = "$environment$deploymentTarget"
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

  approvedTerraformInfrastructure(environment, metricsPublisher) {
    stateStoreInit(environment, subscription, deploymentTarget)

    lock("${productName}-${environmentDeploymentTarget}") {
      stage("Plan ${productName} in ${environmentDeploymentTarget}") {

        def teamConfig = new TeamConfig(this)
        teamName = teamConfig.getName(product)
        def contactSlackChannel = teamConfig.getContactSlackChannel(product)

        def builtFrom = env.GIT_URL ?: 'unknown'
        pipelineTags = new TerraformTagMap([environment: environment, changeUrl: changeUrl, '"Team Name"': teamName, BuiltFrom: builtFrom, contactSlackChannel: contactSlackChannel]).toString()
        log.info "Building with following input parameters: common_tags='$pipelineTags'; product='$product'; component='$component'; deploymentNamespace='$deploymentNamespace'; deploymentTarget='$deploymentTarget' environment='$environment'; subscription='$subscription'; planOnly='$planOnly'"

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

        sh """
          terraform init -reconfigure \
            -backend-config "storage_account_name=${env.STORE_sa_name_template}${subscription}" \
            -backend-config "container_name=${env.STORE_sa_container_name_template}${environmentDeploymentTarget}" \
            -backend-config "resource_group_name=${env.STORE_rg_name_template}-${subscription}" \
            -backend-config "key=${productName}/${environmentDeploymentTarget}/terraform.tfstate"
        """

        env.TF_VAR_ilbIp = 'TODO remove after some time'
        
        sh "terraform get -update=true"
        sh "terraform plan -out tfplan -var 'common_tags=${pipelineTags}' -var 'env=${environment}' -var 'subscription=${subscription}' -var 'deployment_namespace=${deploymentNamespace}' -var 'product=${product}' -var 'component=${component}'" +
          (fileExists("${environment}.tfvars") ? " -var-file=${environment}.tfvars" : "")
      }
      if (!planOnly) {
        stage("Apply ${productName} in ${environmentDeploymentTarget}") {
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
        log.warning "Skipping apply due to planOnly flag set"
    }
  }
}
