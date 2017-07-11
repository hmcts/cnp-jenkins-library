package uk.gov.hmcts.contino


class WebAppDeploy implements Serializable {

  def steps
  def product
  def defaultRemote = "azure"
  def app

  WebAppDeploy(steps, product, app){

    this.app = app
    this.product = product
    this.steps = steps
  }

  def deploy(env, hostingEnv) {

    return steps.withCredentials(
        [[$class: 'UsernamePasswordMultiBinding',
          credentialsId: 'WebAppDeployCredentials',
          usernameVariable: 'GIT_USERNAME',
          passwordVariable: 'GIT_PASSWORD']]) {

        def appUrl = "${product}-${app}-${env}"
        steps.sh("git remote add ${defaultRemote} \"https://${steps.env.GIT_USERNAME}:${steps.env.GIT_PASSWORD}@${appUrl}.scm.${hostingEnv}.p.azurewebsites.net/${appUrl}.git\"")
        steps.sh("git show-ref")
        steps.sh("git status")
        steps.sh("git push ${defaultRemote} master")
    }
  }
}
