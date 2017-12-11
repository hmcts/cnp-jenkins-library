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
def call(Closure body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
//  body()

  echo "${config}"
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

  withSubscription(config.subscription) {

    stage("Check/Init state store '-${config.environment}' in '${config.subscription}'") {
      stateStoreInit(config.environment)
    }

    body()

    lock("${config.productName}-${config.environment}") {
      stage("Plan ${config.productName}-${config.environment} in ${config.subscription}") {
        if (env.STORE_rg_name_template != null &&
          env.STORE_sa_name_template != null &&
          env.STORE_sa_container_name_template != null) {
          log.warning("Using following stateStore={" +
            "'rg_name': '${env.STORE_rg_name_template}-${config.environment}', " +
            "'sa_name': '${env.STORE_sa_name_template}-${config.environment}', " +
            "'sa_container_name': '${env.STORE_sa_container_name_template}-${config.environment}'}")
        } else
          throw new Exception("State store name details not found in environment variables?")

        sh "terraform init -reconfigure -backend-config " +
          "\"storage_account_name=${env.STORE_sa_name_template}-${config.environment}\" " +
          "-backend-config \"container_name=${STORE_sa_container_name_template}-${config.environment}\" " +
          "-backend-config \"resource_group_name=${env.STORE_rg_name_template}-${config.environment}\" " +
          "-backend-config \"key=${config.productName}/${config.environment}/terraform.tfstate\""

        sh "terraform get -update=true"
        sh "terraform plan -var 'env=${config.environment}' -var 'name=${config.productName}'" +
          (fileExists("${config.environment}.tfvars") ? " var-file=${config.environment}.tfvars" : "")

        if (!config.planOnly) {
          sh "terraform apply -var 'env=${config.environment}' -var 'name=${config.productName}'" +
            (fileExists("${config.environment}.tfvars") ? " var-file=${config.environment}.tfvars" : "")
        } else
          log.warning "Skipping apply due to planOnly flag set"
      }
    }
  }
}
