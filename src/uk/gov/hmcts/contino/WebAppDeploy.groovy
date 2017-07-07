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

    return steps.withCredentials(
        [[$class: 'UsernamePasswordMultiBinding',
          credentialsId: 'WebAppDeployCredentials',
          usernameVariable: 'GIT_USERNAME',
          passwordVariable: 'GIT_PASSWORD']]) {

        steps.echo("${steps.env}")
        steps.sh("git remote add ${defaultRemote} \"https://\$GIT_USERNAME:\$GIT_PASSWORD@${product}-${env}.scm.${product}-${env}.p.azurewebsites.net/${product}-${env}.git\"")

        steps.sh("git push ${defaultRemote} master")
    }
  }
}
