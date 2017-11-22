import uk.gov.hmcts.contino.Terraform

def call(Map args = [:]) {
  def product = args.product
  def slackChannel = args.slackChannel

  try {

    def terraform = new Terraform(this, product)
    node {
      stage('Checkout') {
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
  } catch(err) {
    if (slackChannel) {
      notifyBuildFailure channel: slackChannel
    }
    throw err
  }

  if (slackChannel) {
    notifyBuildFixed channel: slackChannel
  }
}
