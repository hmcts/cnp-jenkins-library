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
    setupTerraform()
    runTerraformWithCreds('fmt --diff=true > diff.out')
    steps.sh 'if [ ! -s diff.out ]; then echo "Initial Linting OK ..."; else echo "Linting errors found while running terraform fmt --diff=true..." && cat diff.out ; fi'
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
    }
    else
      throw new Exception("Cannot apply for ${env}. You can only apply 'dev', 'test' or 'prod' on master branch or something else on other branch")
  }

  private java.lang.Boolean canApply(env) {
    def envAllowedOnMasterBranchOnly = env in ['dev', 'prod', 'test']

    return ((envAllowedOnMasterBranchOnly && steps.env.BRANCH_NAME == 'master') ||
            (!envAllowedOnMasterBranchOnly && steps.env.BRANCH_NAME != 'master'))
  }

  private def init(env) {
    if (this.product == null)
      throw new Exception("'product' is null! Library can only run as module helper in this case!")
    def stateStoreConfig = getStateStoreConfig(env)

    return runTerraformWithCreds("init -reconfigure -backend-config " +
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
    def stateStoreConfig = stateStores.find { s -> s.env == env }

    if (stateStoreConfig == null) {
      throw new Exception("State storage for ${env} not found. Is it configured?")
    }

    return stateStoreConfig
  }

  private runTerraformWithCreds(args) {
    return steps.ansiColor('xterm') {
      steps.withCredentials([
          [$class: 'StringBinding', credentialsId: 'sp_password', variable: 'ARM_CLIENT_SECRET'],
          [$class: 'StringBinding', credentialsId: 'tenant_id', variable: 'ARM_TENANT_ID'],
          [$class: 'StringBinding', credentialsId: 'contino_github', variable: 'TOKEN'],
          [$class: 'StringBinding', credentialsId: 'subscription_id', variable: 'ARM_SUBSCRIPTION_ID'],
          [$class: 'StringBinding', credentialsId: 'object_id', variable: 'ARM_CLIENT_ID']]) {

        steps.sh("terraform ${args}")
      }
    }
  }

  private setupTerraform() {
    def tfHome = steps.tool name: 'Terraform', type: 'com.cloudbees.jenkins.plugins.customtools.CustomTool'
    steps.env.PATH = "${tfHome}:${steps.env.PATH}"
  }

}
