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

    if (env in stateStores) {

      def stateStoreConfig = stateStores[env]
      def state_store_resource_group = stateStoreConfig["resourceGroup"]
      def state_store_account = stateStoreConfig["storageAccount"]
      def state_store_container = stateStoreConfig["container"]

      return runTerraformWithCreds("init -backend-config \"storage_account_name=${state_store_account}\" -backend-config \"container_name=${state_store_container}\" -backend-config \"resource_group_name=${state_store_resource_group}\" -backend-config \"key=${this.product}/${env}/terraform.tfstate\"")
    }
    error "$env does not have state storage configured"
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
