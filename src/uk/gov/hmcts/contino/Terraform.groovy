package uk.gov.hmcts.contino

import groovy.json.JsonSlurperClassic


class Terraform implements Serializable {

  def steps
  def product
/***
 *
 * @param steps Jenkins pipe
 * @param product product stack to run
 */
  Terraform(steps, product) {
    this.steps = steps
    this.product = product
  }

  Terraform(jenkinsPipeline) {
    this.steps = jenkinsPipeline
  }

  def lint() {
    runTerraformWithCreds('fmt --diff=true > diff.out')
    steps.sh 'if [ ! -s diff.out ]; then echo "Initial Linting OK ..."; else echo "Linting errors found while running terraform fmt --diff=true... Applying terraform fmt first" && cat diff.out &&  terraform fmt; fi'
    return runTerraformWithCreds('validate')
  }

/***
 * Run a Terraform init and plan
 * @param env Environment to run plan against
 * @return
 */
  def plan(env) {
    if (this.product == null)
      throw new Exception("'product' is null! Library can only run as module helper in this case!")

    init(env)
    runTerraformWithCreds("get -update=true")

    return runTerraformWithCreds(configureArgs(env, "plan -var 'env=${env}' -var 'name=${product}'"))
  }

  /***
   * Run a Terraform apply, based on a previous apply
   * @param env Environment to run apply against
   * @return
   */
  def apply(env) {
    if (canApply(env)) {
      if (this.product == null)
        throw new Exception("'product' is null! Library can only run as module helper in this case!")
      return runTerraformWithCreds(configureArgs(env, "apply -var 'env=${env}' -var 'name=${product}'"))
    } else
      throw new Exception("You cannot apply for Environment: '${env}' on branch '${steps.env.BRANCH_NAME}'. ['dev', 'test', 'prod'] are reserved for master branch, try other name")
  }

  private java.lang.Boolean canApply(String env) {
    def envAllowedOnMasterBranchOnly = env in ['dev', 'prod', 'test']
    logMessage("canApply: on branch: ${steps.env.BRANCH_NAME}; env: ${env}; allowed: ${envAllowedOnMasterBranchOnly}")
    return ((envAllowedOnMasterBranchOnly && steps.env.BRANCH_NAME == 'master') ||
      (!envAllowedOnMasterBranchOnly && steps.env.BRANCH_NAME != 'master'))
  }

  private def init(env) {
    if (this.product == null)
      throw new Exception("'product' is null! Library can only run as module helper in this case!")
    def stateStoreConfig = getStateStoreConfig(env)

    return runTerraformWithCreds(env, "init -reconfigure -backend-config " +
      "\"storage_account_name=${stateStoreConfig.storageAccount}\" " +
      "-backend-config \"container_name=${stateStoreConfig.container}\" " +
      "-backend-config \"resource_group_name=${stateStoreConfig.resourceGroup}\" " +
      "-backend-config \"key=${this.product}/${env}/terraform.tfstate\"")
  }

  private def configureArgs(env, args) {
    if (steps.fileExists("${env}.tfvars")) {
      args = "${args} var-file=${env}.tfvars"
    }
    return args
  }

  private def getStateStoreConfig(env) {
    def stateStores = new JsonSlurperClassic().parseText(steps.libraryResource('uk/gov/hmcts/contino/state-storage.json'))
    if (!canApply(env))
      throw new Exception("You cannot apply for Environment: '${env}' on branch '${steps.env.BRANCH_NAME}'. ['dev', 'test', 'prod'] are reserved for master branch, try other name")

    def stateStoreConfig = stateStores.find { s -> s.env == env }

    if (stateStoreConfig == null) {
      throw new Exception("State storage for ${env} not found. Is it configured?")
    }
    logMessage("Using following stateStores=$stateStores")

    return stateStoreConfig
  }

  void logMessage(GString gString) {
    steps.sh("echo -e '\\e[34m$gString'")
  }

  private runTerraformWithCreds(env, args) {
    setupTerraform()
    return steps.ansiColor('xterm') {
      steps.withCredentials([azureServicePrincipal('jenkinsSP')]) {
        def subscription = 'nonprod'
        if (env == 'prod')
          subscription = 'prod'

        steps.env.ARM_CLIENT_ID = getSecretValue("${subscription}-client-id")
        steps.env.ARM_CLIENT_SECRET = getSecretValue("${subscription}-client-secret")
        steps.env.ARM_TENANT_ID = getSecretValue("${subscription}-tenant-id")
        steps.env.ARM_SUBSCRIPTION_ID = getSecretValue("${subscription}-subscription-id")

        steps.env.AZURE_CLIENT_ID = steps.env.ARM_CLIENT_ID
        steps.env.AZURE_CLIENT_SECRET = steps.env.ARM_CLIENT_SECRET
        steps.env.AZURE_TENANT_ID = steps.env.ARM_TENANT_ID
        steps.env.AZURE_SUBSCRIPTION_ID = steps.env.ARM_SUBSCRIPTION_ID

        steps.sh("terraform ${args}")
      }
    }
  }

  private String getSecretValue(var) {
    resp = steps.sh(script: "az keyvault secret show --vault-name 'jenkins-vault' --name ${var}", returnStdout: true).trim()
    secret = new JsonSlurperClassic().parseText(resp)
    return secret.value
  }

  private setupTerraform() {
    // The tool name refers to the label in the Custom Tool Installation on Jenkins
    def tfHome = steps.tool name: 'Terraform 10', type: 'com.cloudbees.jenkins.plugins.customtools.CustomTool'
    steps.env.PATH = "${tfHome}:${steps.env.PATH}"
  }

}

