package uk.gov.hmcts.contino


class WebAppDeploy implements Serializable {

  def steps
  def product
  def defaultRemote = "azure"

  WebAppDeploy(steps, product){

    this.product = product
    this.steps = steps
  }

  def deploy(env) {

    return steps.withCredentials([
        steps.usernamePassword(credentialsId: 'WebAppDeployCredentials',
        usernameVariable: 'GIT_USERNAME',
        passwordVariable: 'GIT_PASSWORD')]) {

      steps.sh("\"git remote add ${defaultRemote} \"https://${GIT_USERNAME}:${GIT_PASSWORD}@${product}-${env}.scm.${product}-${env}.p.azurewebsites.net/${product}-${env}.git\"")

       teps.sh("git push ${defaultRemote} master")
    }
  }
}
