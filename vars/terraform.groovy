package uk.gov.hmcts.contino
import groovy.json.JsonSlurperClassic

class terraform implements Serializable {

  private steps

  def ini(pipelineHandler) {
    this.steps = pipelineHandler
  }

  def lint() {
    steps.sh 'terraform fmt --diff=true > diff.out'
    steps.sh 'if [ ! -s diff.out ]; then echo "Initial Linting OK ..."; else echo "Linting errors found while running terraform fmt --diff=true... Applying terraform fmt first" && cat diff.out &&  terraform fmt; fi'
    steps.sh 'terraform validate'
  }

  def plan(envName) {
    if (steps.product == null)
      throw new Exception("'product' variable was not defined! Cannot plan without a product name")

    def stateStoreConfig = getStateStoreConfig(envName)

    steps.sh "terraform init -reconfigure -backend-config " +
      "\"storage_account_name=${stateStoreConfig.storageAccount}\" " +
      "-backend-config \"container_name=${stateStoreConfig.container}\" " +
      "-backend-config \"resource_group_name=${stateStoreConfig.resourceGroup}\" " +
      "-backend-config \"key=${steps.product}/${envName}/terraform.tfstate\""

    steps.sh "terraform get -update=true"
    steps.sh("terraform " + configureArgs(envName, "plan -var 'env=${envName}' -var 'name=${steps.product}'"))
  }

  private def getStateStoreConfig(envName) {
    def stateStores = new JsonSlurperClassic().parseText(steps.libraryResource('uk/gov/hmcts/contino/state-storage.json'))

    if (canApply(envName)) {
      def stateStoreConfig = stateStores.find { s -> s.env == envName }

      if (stateStoreConfig != null) {
        logMessage("Using following stateStores=$stateStores")
        return stateStoreConfig
      }
      else if (stateStoreConfig == null && steps.env.BRANCH_NAME != 'master') {
        stateStoreConfig = stateStores.find { s -> s.env == 'default' }
        stateStoreConfig.env = envName
        logMessage("Using following stateStores=$stateStores")
        return stateStoreConfig
      }
      else
        throw new Exception("State storage for ${envName} not found. Is it configured?")
    }
    else
      throw new Exception("You cannot apply for Environment: '${envName}' on branch '${steps.env.BRANCH_NAME}'. ['dev', 'test', 'prod'] are reserved for master branch, try other name")
  }

  void logMessage(GString gString) {
    steps.sh("echo -e '\\e[1m\\e[34m${gString}'")
  }

  private java.lang.Boolean canApply(String envName) {
    def envAllowedOnMasterBranchOnly = envName in ['dev', 'prod', 'test']
    steps.sh("echo 'canApply: on branch: ${steps.env.BRANCH_NAME}; env: ${envName}; allowed: ${envAllowedOnMasterBranchOnly}'")
    return ((envAllowedOnMasterBranchOnly && steps.env.BRANCH_NAME == 'master') ||
      (!envAllowedOnMasterBranchOnly && steps.env.BRANCH_NAME != 'master'))
  }

  private def configureArgs(envName, args) {
    if (steps.fileExists("${envName}.tfvars")) {
      args = "${args} var-file=${envName}.tfvars"
    }
    return args
  }

  /***
   * Run a Terraform apply, based on a previous apply
   * @param envName Environment to run apply against
   * @return
   */
  def apply(envName) {
    if (canApply(envName)) {
      if (steps.product == null)
        throw new Exception("'product' variable was not defined! Cannot apply without a product name")
      steps.sh "terraform " + configureArgs(envName, "apply -var 'env=${envName}' -var 'name=${steps.product}'")
    } else
      throw new Exception("You cannot apply for Environment: '${envName}' on branch '${steps.env.BRANCH_NAME}'. " +
        "['dev', 'test', 'prod'] are reserved for master branch, try other name")
  }

}
