package uk.gov.hmcts.contino

class Terraform implements Serializable {

  def steps
  def product

  Terraform(steps, product){

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
  def init(env, state_store_resource_group, state_store_account, state_store_container ) {
    setupTerraform()
    return steps.withCredentials([
                        [$class: 'StringBinding', credentialsId: 'sp_password', variable: 'ARM_CLIENT_SECRET'],
                        [$class: 'StringBinding', credentialsId: 'tenant_id', variable: 'ARM_TENANT_ID'],
                        [$class: 'StringBinding', credentialsId: 'contino_github', variable: 'TOKEN'],
                        [$class: 'StringBinding',credentialsId: 'subscription_id', variable: 'ARM_SUBSCRIPTION_ID'],
                        [$class: 'StringBinding', credentialsId: 'object_id', variable: 'ARM_CLIENT_ID']]) {

         steps.sh("terraform init -backend-config \"storage_account_name=${state_store_account}\" -backend-config \"container_name=${state_store_container}\" -backend-config \"resource_group_name=${state_store_resource_group}\" -backend-config \"key=${this.product}/${env}/terraform.tfstate\"")
      }
  }


/***
   *
   * @param env
   * @return
   */
  def plan(env) {
    setupTerraform()
    steps.sh("terraform get -update=true")
    return steps.sh("terraform plan -var 'env=${env}'")

  }

  /***
   *
   * @param env
   * @return
   */
  def apply(env){
    setupTerraform()
    if (env.BRANCH_NAME == 'master' ) {
      return steps.sh("terraform apply -var 'env=${env}'")
    }
  }

  private setupTerraform() {
    def tfHome = this.steps.tool name: 'Terraform', type: 'com.cloudbees.jenkins.plugins.customtools.CustomTool'

    this.steps.env.PATH = "${tfHome}:${this.steps.env.PATH}"
  }
}
