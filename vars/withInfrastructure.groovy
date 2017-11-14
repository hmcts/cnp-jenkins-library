import uk.gov.hmcts.contino.Terraform

def call(String product) {

  def terraform = new Terraform(this, product)
  node {
    stage('Checkout') {
      def secrets = [
        [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'prod-client-id', envVariable: 'AZURE_CLIENT_ID']
      ]

      wrap([$class: 'AzureKeyVaultBuildWrapper', azureKeyVaultSecrets: secrets]) {
        sh 'echo CLIENT_ID: $AZURE_CLIENT_ID'
      }
      deleteDir()
      checkout scm
    }
    lock("${product}-dev") {

      stage("Terraform Plan - Dev") {

        terraform.plan("dev")

      }
      stage("Terraform Apply - Dev") {

        terraform.apply("dev")

      }
    }
    if (env.BRANCH_NAME == 'master') {
      lock("${product}-prod") {
        stage('Terraform Plan - Prod') {

          terraform.plan("prod")

        }
        stage('Terraform Apply - Prod') {

          terraform.apply("prod")

        }
      }
    }
  }
}
