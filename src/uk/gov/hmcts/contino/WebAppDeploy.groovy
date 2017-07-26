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
        steps.sh("git remote add ${defaultRemote}-${env} \"https://${steps.env.GIT_USERNAME}:${steps.env.GIT_PASSWORD}@${appUrl}.scm.${hostingEnv}.p.azurewebsites.net/${appUrl}.git\"")
        steps.sh("git checkout ${steps.env.BRANCH_NAME}")
        steps.sh("git push ${defaultRemote}-${env}  master")
    }
  }

  def deployJavaWebApp(env, hostingEnv, jarPath, springConfigPath, iisWebConfig) {

    return steps.withCredentials(
      [[$class: 'UsernamePasswordMultiBinding',
        credentialsId: 'WebAppDeployCredentials',
        usernameVariable: 'GIT_USERNAME',
        passwordVariable: 'GIT_PASSWORD']]) {

      def appUrl = "${product}-${app}-${env}"
      steps.sh("git remote add ${defaultRemote}-${env} \"https://${steps.env.GIT_USERNAME}:${steps.env.GIT_PASSWORD}@${appUrl}.scm.${hostingEnv}.p.azurewebsites.net/${appUrl}.git\"")
      steps.sh("git checkout ${steps.env.BRANCH_NAME}")
      steps.sh("git add  ${jarPath}")
      steps.sh("git add  ${springConfigPath}")
      steps.sh("git add  ${iisWebConfig}")
      steps.sh("git config user.email 'jenkinsmoj@contino.io'")
      steps.sh("git config user.name 'jenkinsmoj'")
      steps.sh("git commit -m 'Deploying ${steps.env.BUILD_NUMBER}'")
      steps.sh("git checkout ${steps.env.BRANCH_NAME}")
      steps.sh("git push ${defaultRemote}-${env}  master")
    }
  }
}
