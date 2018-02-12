#!groovy
import groovy.json.JsonSlurperClassic

def call(productName, environment, planOnly = false, subscription) {
  log.info "Building with following input parameters: productName='$productName'; environment='$environment'; subscription='$subscription'; planOnly='$planOnly'"

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
        "-backend-config \"container_name=${STORE_sa_container_name_template}${environment}\" " +
        "-backend-config \"resource_group_name=${env.STORE_rg_name_template}-${subscription}\" " +
        "-backend-config \"key=${productName}/${environment}/terraform.tfstate\"" +
        (fileExists("${environment}.tfvars") ? " -var-file=${environment}.tfvars" : "")

      sh "terraform get -update=true"
      sh "terraform plan -var 'env=${environment}' -var 'name=${productName}' -var 'subscription=${subscription}'" +
        (fileExists("${environment}.tfvars") ? " -var-file=${environment}.tfvars" : "")

      if (!planOnly) {
        stage("Apply ${productName}-${environment} in ${environment}") {
          sh "terraform apply -auto-approve -var 'env=${environment}' -var 'name=${productName}' -var 'subscription=${subscription}'" +
            (fileExists("${environment}.tfvars") ? " -var-file=${environment}.tfvars" : "")
          parseResult = ""
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
