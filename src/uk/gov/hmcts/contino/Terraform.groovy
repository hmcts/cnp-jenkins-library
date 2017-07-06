package uk.gov.hmcts.contino

import groovy.json.JsonSlurper



class Terraform implements Serializable {

  def steps
  def product
/***
 *
 * @param steps Jenkins steps
 * @param product product stack to run
 */
  Terraform(steps, product) {

    this.steps = steps
    this.product = product
  }


/***
 * Run a Terraform init and plan
 * @param  env Environment to run plan against
 * @return
 */
  def plan(env) {
    init(env)
    runTerraformWithCreds("get -update=true")
    return runTerraformWithCreds("plan -var 'env=${env}'")

  }

  /***
   * Run a Terraform apply, based on a previous apply
   * @param env Environment to run apply against
   * @return
   */
  def apply(env) {

    if (steps.env.BRANCH_NAME == 'master') {
      return runTerraformWithCreds("apply -var 'env=${env}'")
    }
  }

  private def init(env) {

    def stateStoreConfig = getStateStoreConfig(env)
    steps.echo(stateStoreConfig)

      return runTerraformWithCreds("init -backend-config " +
        "\"storage_account_name=${stateStoreConfig.storageAccount}\" " +
        "-backend-config \"container_name=${stateStoreConfig.container}\" " +
        "-backend-config \"resource_group_name=${stateStoreConfig.resourceGroup}\" " +
        "-backend-config \"key=${this.product}/${env}/terraform.tfstate\"")


  }

  private def getStateStoreConfig(env) {


    def stateStores = new JsonSlurper().parseText(steps.libraryResource('uk/gov/hmcts/contino/state-storage.json'))
    steps.echo(stateStores)
    def stateStoreConfig = stateStores.find { s -> s.env == env }
    steps.echo(stateStoreConfig)
    if(stateStoreConfig == null) {
      throw new Exception("State storage for ${env} not found. Is it configured?")
    }
    return stateStoreConfig
  }

  private runTerraformWithCreds(args) {

    setupTerraform()

    println("Running terraform ${args}")

    return steps.withCredentials([
      [$class: 'StringBinding', credentialsId: 'sp_password', variable: 'ARM_CLIENT_SECRET'],
      [$class: 'StringBinding', credentialsId: 'tenant_id', variable: 'ARM_TENANT_ID'],
      [$class: 'StringBinding', credentialsId: 'contino_github', variable: 'TOKEN'],
      [$class: 'StringBinding', credentialsId: 'subscription_id', variable: 'ARM_SUBSCRIPTION_ID'],
      [$class: 'StringBinding', credentialsId: 'object_id', variable: 'ARM_CLIENT_ID']]) {

      steps.sh("terraform ${args}")
    }

  }

  private setupTerraform() {
    def tfHome = this.steps.tool name: 'Terraform', type: 'com.cloudbees.jenkins.plugins.customtools.CustomTool'

    this.steps.env.PATH = "${tfHome}:${this.steps.env.PATH}"

  }
}
