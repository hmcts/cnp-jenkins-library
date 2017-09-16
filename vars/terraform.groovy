import groovy.json.JsonSlurperClassic

class terraform implements Serializable {

  def pipeHandle

  def terraform(pipelineHandle) {
    this.pipeHandle = pipelineHandle
    sh "echo 'terraform constructor: pipeHandle initialised with ${pipeHandle}'"
  }

  def lint() {
    sh 'terraform fmt --diff=true > diff.out'
    sh 'if [ ! -s diff.out ]; then echo "Initial Linting OK ..."; else echo "Linting errors found while running terraform fmt --diff=true... Applying terraform fmt first" && cat diff.out &&  terraform fmt; fi'
    sh 'terraform validate'
  }

  def plan(env) {
    if (pipeHandle.product == null)
      throw new Exception("'product' variable was not defined! Cannot plan without a product name")

    def stateStoreConfig = getStateStoreConfig(env)

    sh "terraform init -reconfigure -backend-config " +
      "\"storage_account_name=${stateStoreConfig.storageAccount}\" " +
      "-backend-config \"container_name=${stateStoreConfig.container}\" " +
      "-backend-config \"resource_group_name=${stateStoreConfig.resourceGroup}\" " +
      "-backend-config \"key=${pipeHandle.product}/${env}/terraform.tfstate\""

    sh "terraform get -update=true"
    sh("terraform " + configureArgs(env, "plan -var 'env=${env}' -var 'name=${pipeHandle.product}'"))
  }

  private def getStateStoreConfig(env) {
    def stateStores = new JsonSlurperClassic().parseText(libraryResource('uk/gov/hmcts/contino/state-storage-template.json'))
    if (canApply(env)) {
      stateStores += ['env': env]
    } else
      throw new Exception("You cannot apply for Environment: '${env}' on branch '${env.BRANCH_NAME}'. ['dev', 'test', 'prod'] are reserved for master branch, try other name")

  }

  private java.lang.Boolean canApply(String env) {
    def envAllowedOnMasterBranchOnly = env in ['dev', 'prod', 'test']
    sh("echo 'canApply: on branch: ${env.BRANCH_NAME}; env: ${env}; allowed: ${envAllowedOnMasterBranchOnly}'")
    return ((envAllowedOnMasterBranchOnly && env.BRANCH_NAME == 'master') ||
      (!envAllowedOnMasterBranchOnly && env.BRANCH_NAME != 'master'))
  }

  private def configureArgs(env, args) {
    if (fileExists("${env}.tfvars")) {
      args = "${args} var-file=${env}.tfvars"
    }
    return args
  }
}

/*
def call(String command) {
  sh "echo 'terraform lib: executing ${command}'"

  sh "terraform ${command}"
}
*/
