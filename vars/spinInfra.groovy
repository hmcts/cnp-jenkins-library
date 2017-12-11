#!groovy

// @param productName
// @param environment
//        determines the suffix of storage account groups used and the suffix for built resources
// @param subscription
//        subscription is determined automatically:
//          if environment = prod*  -> subscription = "prod"
//          else subscription = "nonprod"
//        if subscription is given as parameter the above is overwritten
// @param planOnly
//        will only run terraform plan, apply will be skipped
//        Default: false
def call(productName, environment, subscription = "nonprod", planOnly = false, Closure body) {
/* will try to make it work with parameters defined at the beginning of the closure for cleaner parametrisation

// evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  if (!config.containsKey("productName"))
    throw new Exception("You have not specified a productName therefore not sure what to build! Stopping execution...")
  if (!config.containsKey("environment"))
    throw new Exception("You have not specified a environment therefore not sure what to build! Stopping execution...")
  //default subscription = nonprod if not otherwise specified
  if (!config.containsKey("subscription"))
    config.subscription = "nonprod"
  //default planOnly = false
  if (!config.containsKey("planOnly"))
    config.planOnly = false
*/
  env.ENVIRONMENT = environment

  withSubscription(subscription) {

    stage("Check/Init state store '-${environment}' in '${subscription}'") {
      stateStoreInit(environment)
    }

    body.call()

    lock("${productName}-${environment}") {
      stage("Plan ${productName}-${environment} in ${subscription}") {
        if (env.STORE_rg_name_template != null &&
          env.STORE_sa_name_template != null &&
          env.STORE_sa_container_name_template != null)
        {
          log.warning("Using following stateStore={" +
            "'rg_name': '${env.STORE_rg_name_template}-${environment}', " +
            "'sa_name': '${env.STORE_sa_name_template}-${environment}', " +
            "'sa_container_name': '${env.STORE_sa_container_name_template}-${environment}'}")
        } else
          throw new Exception("State store name details not found in environment variables?")

        sh "terraform init -reconfigure -backend-config " +
          "\"storage_account_name=${env.STORE_sa_name_template}-${environment}\" " +
          "-backend-config \"container_name=${STORE_sa_container_name_template}-${environment}\" " +
          "-backend-config \"resource_group_name=${env.STORE_rg_name_template}-${environment}\" " +
          "-backend-config \"key=${productName}/${environment}/terraform.tfstate\""

        sh "terraform get -update=true"
        sh "terraform plan -var 'env=${environment}' -var 'name=${productName}'" +
          (fileExists("${environment}.tfvars") ? " var-file=${environment}.tfvars" : "")

        if (!planOnly) {
          stage("Apply ${productName}-${environment} in ${subscription}") {
            sh "terraform apply -var 'env=${environment}' -var 'name=${productName}'" +
              (fileExists("${environment}.tfvars") ? " var-file=${environment}.tfvars" : "")
          }
        } else
          log.warning "Skipping apply due to planOnly flag set"
      }
    }
  }
}
