def call() {
  def tfHome = steps.tool name: 'Terraform', type: 'com.cloudbees.jenkins.plugins.customtools.CustomTool'
  env.PATH = "${tfHome}:${env.PATH}"
  env.TERRAFORM_INITIALIZED = true
}
