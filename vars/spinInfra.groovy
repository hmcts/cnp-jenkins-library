#!groovy

/*
 @param productName
 @param environment
        the suffix of storage account group and rest of created resources
 @param planOnly
        will only run terraform plan, apply will be skipped
        Default: false
*/
def call(productName, subscription, planOnly = false) {
/*  //TODO: make it work with optoinal parameters for planOnly
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
  if (env.SUBSCRIPTION_NAME == null)
    throw new Exception("There is no SUBSCRIPTION_NAME environment variable, are you running inside a withSubscription block?")

  stateStoreInit(subscription)

  lock("${productName}-${subscription}") {
    stage("Plan ${productName}-${subscription} in ${subscription}") {
      if (env.STORE_rg_name_template != null &&
        env.STORE_sa_name_template != null &&
        env.STORE_sa_container_name_template != null) {
        log.warning("Using following stateStore={" +
          "'rg_name': '${env.STORE_rg_name_template}-${subscription}', " +
          "'sa_name': '${env.STORE_sa_name_template}${subscription}', " +
          "'sa_container_name': '${env.STORE_sa_container_name_template}${subscription}'}")
      } else
        throw new Exception("State store name details not found in environment variables?")

      sh 'env|grep "TF_VAR\\|AZURE\\|ARM\\|STORE"'

      sh "terraform init -reconfigure -backend-config " +
        "\"storage_account_name=${env.STORE_sa_name_template}${subscription}\" " +
        "-backend-config \"container_name=${STORE_sa_container_name_template}${subscription}\" " +
        "-backend-config \"resource_group_name=${env.STORE_rg_name_template}-${subscription}\" " +
        "-backend-config \"key=${productName}/${subscription}/terraform.tfstate\""

      sh "terraform get -update=true"
      sh "terraform plan -var 'env=${subscription}' -var 'name=${productName}'" +
        (fileExists("${subscription}.tfvars") ? " var-file=${subscription}.tfvars" : "")

      if (!planOnly) {
        stage("Apply ${productName}-${subscription} in ${subscription}") {
          sh "terraform apply -auto-approve -var 'env=${subscription}' -var 'name=${productName}'" +
            (fileExists("${subscription}.tfvars") ? " var-file=${subscription}.tfvars" : "")
        }
      } else
        log.warning "Skipping apply due to planOnly flag set"
    }
  }
}
