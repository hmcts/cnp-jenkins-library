#!groovy
import groovy.json.JsonSlurperClassic
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.TerraformTagMap
import uk.gov.hmcts.contino.TeamNames

def call(productName, environment, planOnly, subscription) {
  call(productName, null, environment, planOnly, subscription)
}

def call(product, component, environment, planOnly, subscription) {
  def branch = new ProjectBranch(env.BRANCH_NAME)

  def deploymentNamespace = branch.deploymentNamespace()
  def productName = component ? "$product-$component" : product
  def changeUrl = ""

  onPR {
    changeUrl = env.CHANGE_URL
  }

  def teamName = new TeamNames.getName(productName)

  def pipelineTags = new TerraformTagMap([environment: environment, changeUrl: changeUrl, teamName: teamName]).toString()
  log.info "Building with following input parameters: common_tags='$pipelineTags'; product='$product'; component='$component'; deploymentNamespace='$deploymentNamespace'; environment='$environment'; subscription='$subscription'; planOnly='$planOnly'"

  if (env.SUBSCRIPTION_NAME == null)
    throw new Exception("There is no SUBSCRIPTION_NAME environment variable, are you running inside a withSubscription block?")

  stateStoreInit(environment, subscription)

  lock("${productName}-${environment}") {
    stage("Plan ${productName}-${environment} in ${environment}") {
      if (env.STORE_rg_name_template != null &&
        env.STORE_sa_name_template != null &&
        env.STORE_sa_container_name_template != null) {
        log.warning("Using following stateStore={" +
          "'rg_name': '${env.STORE_rg_name_template}-${subscription}', " +
          "'sa_name': '${env.STORE_sa_name_template}${subscription}', " +
          "'sa_container_name': '${env.STORE_sa_container_name_template}${environment}'}")
      } else
        throw new Exception("State store name details not found in environment variables?")

      sh 'env|grep "TF_VAR\\|AZURE\\|ARM\\|STORE"'

      sh "terraform init -reconfigure -backend-config " +
        "\"storage_account_name=${env.STORE_sa_name_template}${subscription}\" " +
        "-backend-config \"container_name=${env.STORE_sa_container_name_template}${environment}\" " +
        "-backend-config \"resource_group_name=${env.STORE_rg_name_template}-${subscription}\" " +
        "-backend-config \"key=${productName}/${environment}/terraform.tfstate\""



      sh "terraform get -update=true"
      sh "terraform plan -var 'common_tags=${pipelineTags}' -var 'env=${environment}' -var 'name=${productName}' -var 'subscription=${subscription}' -var 'deployment_namespace=${deploymentNamespace}' -var 'product=${product}' -var 'component=${component}'" +
        (fileExists("${environment}.tfvars") ? " -var-file=${environment}.tfvars" : "")

      if (!planOnly) {
        stage("Apply ${productName}-${environment} in ${environment}") {
          sh "terraform apply -auto-approve -var 'common_tags=${pipelineTags}' -var 'env=${environment}' -var 'name=${productName}' -var 'subscription=${subscription}' -var 'deployment_namespace=${deploymentNamespace}' -var 'product=${product}' -var 'component=${component}'" +
            (fileExists("${environment}.tfvars") ? " -var-file=${environment}.tfvars" : "")
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
