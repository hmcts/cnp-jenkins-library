package uk.gov.hmcts.contino


class WebAppDeploy implements Serializable {

  def steps
  def product

  WebAppDeploy(steps, product){

    this.product = product
    this.steps = steps
  }

  def deploy(env) {

    return steps.git(
      'credentialsId': "WebAppDeployCredentials",
      'url': "remote add azure \"https://${product}-${env}.scm.${product}-${env}.p.azurewebsites.net/${product}-${env}.git\"")
  }
}
