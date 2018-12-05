package uk.gov.hmcts.contino

import groovy.json.JsonSlurperClassic

/**
 * Deploys Web Applications to Web App Services
 */
class WebAppDeploy implements Serializable {

  public static final String GIT_EMAIL = "jenkinsmoj@contino.io"
  public static final String GIT_USER = "moj-jenkins-user"

  def steps
  def product
  def defaultRemote = "azurerm"
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
  def healthCheck(env, slot) {
    def serviceUrl = getServiceUrl(product, app, env, slot)
    def healthCheckUrl = "${serviceUrl}/health"

    int sleepDuration = 10
    int maxAttempts = 50

    def healthChecker = new HealthChecker(steps)
    healthChecker.check(healthCheckUrl, sleepDuration, maxAttempts)
  }

  /**
   * Deploys the static website in @dir to @env
   * @param env
   * @param dir
   * @return
   */
  def deployStaticSite(env, dir){
    return steps.dir(dir) {
      steps.sh("git init")
      steps.sh("git checkout -B ${branch.branchName}")
      steps.writeFile file: 'deploy.cmd', text: steps.libraryResource('uk/gov/hmcts/contino/yarn-install/deploy.cmd')
      steps.writeFile file: '.deployment', text: steps.libraryResource('uk/gov/hmcts/contino/yarn-install/.deployment')
      steps.sh("git add .")

      pushToService(product, app, env)
    }
  }

  /**
   * Deploys a NodeJs app to @env
   * @param env
   * @return
   */
  def deployNodeJS(env) {
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

    return pushToService(product, app, env)
  }

  /**
   * Deploys a Java Web App. Expects a self hosted Jar or War
   * @param env
   * @return
   */
  def deployJavaWebApp(env) {
    def tempDir = ".tmp_azure_jenkins"

    steps.sh("git checkout ${branch.branchName}")

    steps.sh("mkdir ${tempDir}")

    def status = copyAndReturnStatus('build/libs/*.jar', tempDir)
    if (status != 0) {
      status = copyAndReturnStatus('build/libs/*.war', tempDir)
    }

    if (status != 0) {
      steps.error "deployJavaWebApp expects an executable JAR or WAR deployment, neither was found. status = ${status}"
    }

    checkAndCopy('web.config', tempDir)
    copyIgnore('lib/applicationinsights-*.jar', tempDir)
    copyIgnore('lib/AI-Agent.xml', tempDir)
    // for WebJobs
    copyIgnore('App_Data', tempDir)

    steps.sh("GLOBIGNORE='${tempDir}:.git'; rm -rf *")
    steps.sh("cp -R ${tempDir}/* .")
    steps.sh("rm -rf ${tempDir}")
    steps.sh("git add --all .")

    pushToService(product, app, env)

    return steps.sh('git reset --hard HEAD~1')
  }

  /***
   * Gets the service url
   * @param product
   * @param app
   * @param env
   * @param slot
   * @return
   */
  def getServiceUrl(product, app, env, slot) {
    return "https://${getServiceHost(product, app, env, slot)}"
  }

  def getServiceUrl(env, slot) {
    return getServiceUrl(this.product, this.app, env, slot)
  }

  private def copy(filePath, destinationDir) {
    steps.sh("cp ${filePath} ${destinationDir}")
  }

  private def copyAndReturnStatus(filePath, destinationDir) {
    steps.sh(
      script: "cp ${filePath} ${destinationDir}",
      returnStatus: true
    )
  }

  /**
   * Forces a recursive copy by always returning 0 regardless of errors
   */
  private def copyIgnore(filePath, destinationDir) {
    steps.sh("cp -R ${filePath} ${destinationDir} || :")
  }

  private def checkAndCopy(filePath, destinationDir) {
    if (filePath && steps.fileExists(filePath)) {
      steps.sh("cp ${filePath} " + destinationDir)
    }
  }

  private def getServiceHost(String product, String component, String env, String slot) {
    AppServiceResolver asr = new AppServiceResolver(steps)
    boolean staging = (slot == "staging")

    return asr.getServiceHost(product, component, env, staging)
  }

  private def getServiceName(product, app, env) {
    return "${product}-${app}-${env}"
  }

  private def configureGit() {
    steps.sh("git config user.email '${GIT_EMAIL}'")
    steps.sh("git config user.name '${GIT_USER}'")
    steps.sh("git config http.postBuffer 524288000")
    steps.sh("git commit -m 'Deploying Build #${steps.env.BUILD_NUMBER}' --allow-empty")
  }

  private def pushToService(product, app, env) {
    configureGit()

    def serviceName = getServiceName(product, app, env)

    def az = { cmd -> return steps.sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$steps.env.SUBSCRIPTION_NAME az $cmd", returnStdout: true).trim() }
    def result = az "webapp deployment list-publishing-profiles --name ${serviceName} --slot staging --resource-group ${serviceName} --query \"[?publishMethod=='MSDeploy'].{publishUrl:publishUrl,userName:userName,userPWD:userPWD}|[0]\""
    def profile = new JsonSlurperClassic().parseText(result)

    steps.sh("git -c http.sslVerify=false remote add ${defaultRemote}-${env} 'https://${profile.userName}:${profile.userPWD}@${profile.publishUrl}/${serviceName}.git'")
    steps.sh("git -c http.sslVerify=false push ${defaultRemote}-${env} HEAD:master -f")
  }

}
