package uk.gov.hmcts.contino

/**
 * Deploys Web Applications to Web App Services
 */
class WebAppDeploy implements Serializable {

  public static final String GIT_EMAIL = "jenkinsmoj@contino.io"
  public static final String GIT_USER = "jenkinsmoj"
  public static final String SERVICE_HOST_SUFFIX = "p.azurewebsites.net"
  public static final String DEPLOYMENT_SLOT_SUFFIX = "-staging"
  def steps
  def product
  def defaultRemote = "azure"
  def app
  def branch

  WebAppDeploy(steps, product, app) {
    this.app = app
    this.product = product
    this.steps = steps
    this.branch = new ProjectBranch("${steps.env.BRANCH_NAME}")
  }

  /**
   * Performs a healthcheck on the service on @env. Assumes that the service exposes a /health endpoint
   * @param env
   * @return
   */
  def healthCheck(env) {

    def serviceUrl = getServiceUrl(product, app, env)
    def healthCheckUrl = "${serviceUrl}/health"

    int sleepDuration = 10
    int maxRetries = 10

    int retryCounter = 0

    steps.retry(maxRetries) {
      steps.echo "Attempt number: " + (1 + retryCounter)

      def response = steps.httpRequest(
        acceptType: 'APPLICATION_JSON',
        consoleLogResponseBody: true,
        contentType: 'APPLICATION_JSON',
        timeout: 10,
        url: healthCheckUrl,
        validResponseCodes: '200:599'
      )

      if (response.status > 300) {
        ++retryCounter
        if (retryCounter < maxRetries) {
          steps.sleep sleepDuration
        }
        steps.echo "Service isn’t healthy, will retry up to ${maxRetries} times"
        throw new RuntimeException()
      }
    }
  }

  /**
   * Deploys the static website in @dir to @env
   * @param env
   * @param dir
   * @return
   */
  def deployStaticSite(env, dir){
   return steps.dir(dir) {

     steps.withCredentials(
       [[$class: 'UsernamePasswordMultiBinding',
         credentialsId: 'WebAppDeployCredentials',
         usernameVariable: 'GIT_USERNAME',
         passwordVariable: 'GIT_PASSWORD']]) {

       steps.sh("git init")
       steps.sh("git checkout -b ${branch.branchName}")
       steps.sh("git add .")

       pushToService(product, app, env)
     }
   }
  }

  /**
   * Deploys a NodeJs app to @env
   * @param env
   * @return
   */
  def deployNodeJS(env){
    return deployNodeJS(env, getComputeFor(env))
  }

  /**
   * Deploys a NodeJs app to @env and to the cluster @hostingEnv
   * @param env
   * @param hostingEnv
   * @return
   */

  def deployNodeJS(env, hostingEnv) {

    return steps.withCredentials(
        [[$class: 'UsernamePasswordMultiBinding',
          credentialsId: 'WebAppDeployCredentials',
          usernameVariable: 'GIT_USERNAME',
          passwordVariable: 'GIT_PASSWORD']]) {

        def appUrl = "${product}-${app}-${env}"
        steps.sh("git checkout ${branch.branchName}")

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
        steps.writeFile file: 'deploy.cmd', text: steps.libraryResource('uk/gov/hmcts/contino/yarn-install/deploy.cmd')
        steps.writeFile file: '.deployment', text: steps.libraryResource('uk/gov/hmcts/contino/yarn-install/.deployment')
        steps.sh("git add .")

        pushToService(product, app, env)
    }
  }

  def deployJavaWebApp(env) {
    return steps.withCredentials(
      [[$class: 'UsernamePasswordMultiBinding',
        credentialsId: 'WebAppDeployCredentials',
        usernameVariable: 'GIT_USERNAME',
        passwordVariable: 'GIT_PASSWORD']]) {

      def tempDir = ".tmp_azure_jenkings"

      steps.sh("git checkout ${branch.branchName}")

      steps.sh("mkdir ${tempDir}")

      copy('build/libs/*.jar', tempDir)
      checkAndCopy('web.config', tempDir)

      steps.sh("GLOBIGNORE='${tempDir}:.git'; rm -rf *")
      steps.sh("cp ${tempDir}/* .")
      steps.sh("rm -rf ${tempDir}")
      steps.sh("git add --all .")

      pushToService(product, app, env)
    }

  }

  /**
   * Deploys a Java Web Ppp. Expects a self hosted Jar
   * @param env
   * @param jarPath
   * @param iisWebConfig
   * @return
   */
  def deployJavaWebApp(env, jarPath, iisWebConfig){
    return deployJavaWebApp(env, jarPath, null, iisWebConfig)
  }

  /**
   * Deploys a Java Web Ppp. Expects a self hosted Jar
   * @param env
   * @param hostingEnv
   * @param jarPath
   * @param springConfigPath
   * @param iisWebConfig
   * @return
   */

  def deployJavaWebApp(env, jarPath, springConfigPath, iisWebConfig) {

    return steps.withCredentials(
      [[$class: 'UsernamePasswordMultiBinding',
        credentialsId: 'WebAppDeployCredentials',
        usernameVariable: 'GIT_USERNAME',
        passwordVariable: 'GIT_PASSWORD']]) {

      def tempDir = ".tmp_azure_jenkings"


      steps.sh("git checkout ${branch.branchName}")

      steps.sh("mkdir ${tempDir}")

      checkAndCopy(jarPath, tempDir)
      checkAndCopy(springConfigPath, tempDir)
      checkAndCopy(iisWebConfig, tempDir)

      steps.sh("GLOBIGNORE='${tempDir}:.git'; rm -rf *")
      steps.sh("cp ${tempDir}/* .")
      steps.sh("rm -rf ${tempDir}")
      steps.sh("git add --all .")

      pushToService(product, app, env)
    }
  }

  /***
   * Gets the service url
   * @param product
   * @param app
   * @param env
   * @return
   */
  def getServiceUrl(product, app, env) {
    return "http://${getServiceHost(product, app, env)}"
  }

  def getServiceUrl(env) {
    return getServiceUrl(this.product, this.app, env)
  }

  private def copy(filePath, destinationDir) {
    steps.sh("cp ${filePath} ${destinationDir}")
  }

  private def checkAndCopy(filePath, destinationDir) {
    if (filePath && steps.fileExists(filePath)) {
      steps.sh("cp  ${filePath} " + destinationDir)
    }

  }

  private def getServiceDeploymentHost(product, app, env) {
    def serviceName = getServiceName(product, app, env)
    def hostingEnv = getComputeFor(env)
    return "${serviceName}${DEPLOYMENT_SLOT_SUFFIX}.scm.${hostingEnv}.${SERVICE_HOST_SUFFIX}"
  }

  private def getServiceHost(product, app, env) {
    def computeCluster = getComputeFor(env)
    return "${getServiceName(product, app, env)}.${computeCluster}.${SERVICE_HOST_SUFFIX}"
  }

  private def getServiceName(product, app, env) {
    return "${product}-${app}-${env}"
  }



  private def getComputeFor(env){
    return "core-compute-sample-dev"
  }

  private def gitPushToService(serviceDeploymentHost, serviceName, env) {
    steps.sh("git remote add ${defaultRemote}-${env} \"https://${steps.env.GIT_USERNAME}:${steps.env.GIT_PASSWORD}@${serviceDeploymentHost}/${serviceName}.git\"")
    steps.sh("git push ${defaultRemote}-${env} HEAD:master -f")
  }

  private def configureGit() {
    steps.sh("git config user.email '${GIT_EMAIL}'")
    steps.sh("git config user.name '${GIT_USER}'")
    steps.sh("git commit -m 'Deploying ${steps.env.BUILD_NUMBER}' --allow-empty")
  }

  private def pushToService(product, app, env) {

    configureGit()

    def serviceName = getServiceName(product, app, env)
    def serviceDeploymentHost = getServiceDeploymentHost(product, app, env)

    gitPushToService(serviceDeploymentHost, serviceName, env)
  }

}
