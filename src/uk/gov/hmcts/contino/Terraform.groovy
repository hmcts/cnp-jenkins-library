package uk.gov.hmcts.contino

import groovy.json.JsonSlurper


class Terraform implements Serializable {

  def steps
  def product

  Terraform(steps, product) {

    this.steps = steps
    this.product = product
  }

  /***
   *
   * @param env
   * @param state_store_resource_group
   * @param state_store_account
   * @param state_store_container
   * @return
   */

/***
 *
 * @param env
 * @return
 */
  def plan(env) {
    init(env)
    setupTerraform()
    runTerraformWithCreds("get -update=true")
    return runTerraformWithCreds("plan -var 'env=${env}'")

  }

  /***
   *
   * @param env
   * @return
   */
  def apply(env) {

    if (steps.env.BRANCH_NAME == 'master') {
      return runTerraformWithCreds("apply -var 'env=${env}'")
    }
  }

  private def init(env) {

    def stateStores = new JsonSlurper().parseText(steps.libraryResource('uk/gov/hmcts/contino/state-storage.json'))

    def stateStoreConfig = stateStores.find { s -> s.env == env}
    if (stateStoreConfig != null) {
      return runTerraformWithCreds("init -backend-config " +
        "\"storage_account_name=${stateStoreConfig.storageAccount}\" " +
        "-backend-config \"container_name=${stateStoreConfig.container}\" " +
        "-backend-config \"resource_group_name=${stateStoreConfig.resourceGroup}\" " +
        "-backend-config \"key=${this.product}/${env}/terraform.tfstate\"")
    }

    throw new Exception("State storage for ${env} not found. Is it configured?")
  }

  private runTerraformWithCreds(args) {

    setupTerraform()

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
