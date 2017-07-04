package uk.gov.hmcts.contino

class Terraform implements Serializable {

  def steps
  def product

  Terraform(steps, product){

    this.steps = steps
    this.product = product

    def tfHome = this.steps.tool name: 'Terraform', type: 'com.cloudbees.jenkins.plugins.customtools.CustomTool'

    this.steps.env.PATH = "${tfHome}:${this.steps.env.PATH}"
  }

  /***
   *
   * @param env
   * @param state_store_resource_group
   * @param state_store_account
   * @param state_store_container
   * @return
   */
  def init(env, state_store_resource_group, state_store_account, state_store_container ) {

      return this.steps.sh("terraform init -backend-config \"storage_account_name=${state_store_account}\" -backend-config \"container_name=${state_store_container}\" -backend-config \"resource_group_name=${state_store_resource_group}\" -backend-config \"key=${this.product}/${env}/terraform.tfstate\"")

  }
}
