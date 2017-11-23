import uk.gov.hmcts.contino.Terraform

def call(String product) {

  Terraform terraform = new Terraform(this, product)
  node {
    stage('Checkout') {
      deleteDir()
      checkout scm
    }
    lock("${product}-dev") {

      stage("Terraform Plan - Dev") {

        terraform.plan("dev")

      }

      onMaster {
        stage("Terraform Apply - Dev") {
          terraform.apply("dev")
        }
      }
    }
    onMaster {
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
