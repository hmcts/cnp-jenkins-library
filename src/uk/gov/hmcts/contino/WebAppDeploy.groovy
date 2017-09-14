package uk.gov.hmcts.contino


class WebAppDeploy implements Serializable {

  public static final java.lang.String GIT_EMAIL = "jenkinsmoj@contino.io"
  public static final java.lang.String GIT_USER = "jenkinsmoj"
  def steps
  def product
  def defaultRemote = "azure"
  def app

  WebAppDeploy(steps, product, app){

    this.app = app
    this.product = product
    this.steps = steps
  }

  /*

  */
  def healthCheck(env) {

    computeCluster = getComputeFor(env)
    healthCheckUrl = "http://${product}-${app}-${env}.${computeCluster}.p.azurewebsites.net/health"
    return steps.sh("curl -vf ${healthCheckUrl}")
  }

  private def getComputeFor(env){
    return "core-compute-sample-dev"
  }

  def deployNodeJS(env){
    return deployNodeJS(env, getComputeFor(env))
  }

  def deployNodeJS(env, hostingEnv) {

    return steps.withCredentials(
        [[$class: 'UsernamePasswordMultiBinding',
          credentialsId: 'WebAppDeployCredentials',
          usernameVariable: 'GIT_USERNAME',
          passwordVariable: 'GIT_PASSWORD']]) {

        def appUrl = "${product}-${app}-${env}"
        steps.sh("git remote add ${defaultRemote}-${env} \"https://${steps.env.GIT_USERNAME}:${steps.env.GIT_PASSWORD}@${appUrl}.scm.${hostingEnv}.p.azurewebsites.net/${appUrl}.git\"")
        steps.sh("git checkout ${steps.env.BRANCH_NAME}")

        steps.sh("rm .gitignore")
        steps.sh("echo 'test/*' > .gitignore")
        steps.sh("echo '.sonar/' >> .gitignore")
        steps.sh("echo '.sonarlint/' >> .gitignore")
        steps.sh("echo '/npm-debug.log*' >> .gitignore")
        steps.sh("echo 'jsconfig.json' >> .gitignore")
        steps.sh("echo '*.tmp' >> .gitignore")
        steps.sh("echo '/node_modules/' >> .gitignore")
        steps.sh("echo '/log/' >> .gitignore")
        steps.sh("echo '/lib/' >> .gitignore")
        steps.sh("echo 'coverage' >> .gitignore")
        steps.sh("git add .")

        steps.sh("git config user.email 'jenkinsmoj@contino.io'")
        steps.sh("git config user.name 'jenkinsmoj'")
        steps.sh("git commit -m 'Deploying ${steps.env.BUILD_NUMBER}'")
        steps.sh("git push ${defaultRemote}-${env}  master -f")
    }
  }

  def deployJavaWebApp(env, jarPath, springConfigPath, iisWebConfig){
    return deployJavaWebApp(env, getComputeFor(env), jarPath, springConfigPath, iisWebConfig)
  }

  def deployJavaWebApp(env, hostingEnv, jarPath, springConfigPath, iisWebConfig) {

    return steps.withCredentials(
      [[$class: 'UsernamePasswordMultiBinding',
        credentialsId: 'WebAppDeployCredentials',
        usernameVariable: 'GIT_USERNAME',
        passwordVariable: 'GIT_PASSWORD']]) {

      def tempDir = ".tmp_azure_jenkings"

      def appUrl = "${product}-${app}-${env}"

      steps.sh("git remote add ${defaultRemote}-${env} \"https://${steps.env.GIT_USERNAME}:${steps.env.GIT_PASSWORD}@${appUrl}.scm.${hostingEnv}.p.azurewebsites.net/${appUrl}.git\"")
      steps.sh("git checkout ${steps.env.BRANCH_NAME}")

      steps.sh("mkdir ${tempDir}")

      checkAndCopy(jarPath, tempDir)
      checkAndCopy(springConfigPath, tempDir)
      checkAndCopy(iisWebConfig, tempDir)

      steps.sh("GLOBIGNORE='${tempDir}:.git'; rm -rf *")
      steps.sh("cp ${tempDir}/* .")
      steps.sh("rm -rf ${tempDir}")
      steps.sh("git add --all .")

      steps.sh("git config user.email '" + GIT_EMAIL + "'")
      steps.sh("git config user.name '" + GIT_USER + "'")
      steps.sh("git commit -m 'Deploying ${steps.env.BUILD_NUMBER}'")
      steps.sh("git push ${defaultRemote}-${env}  master -f")
    }
  }

  private def checkAndCopy(filePath, destinationDir) {
    if (fileExists(filePath)) {
      steps.sh("cp  ${filePath} " + destinationDir)
    }
  }
}
