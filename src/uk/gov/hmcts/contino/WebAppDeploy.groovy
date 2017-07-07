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

    steps.sh("\"git remote add ${defaultRemote} \"https://$GIT_DEPLPOY_USERNAME:$GIT_DEPLPOY_PASSWORD@${product}-${env}.scm.${product}-${env}.p.azurewebsites.net/${product}-${env}.git\"")

    return steps.sh("git push ${defaultRemote} master")
  }
}
