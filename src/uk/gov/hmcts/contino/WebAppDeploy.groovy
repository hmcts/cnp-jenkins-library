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

  def deployJavaWebApp(env, hostingEnv, jarPath, springConfigPath, iisWebConfig) {

    return steps.withCredentials(
      [[$class: 'UsernamePasswordMultiBinding',
        credentialsId: 'WebAppDeployCredentials',
        usernameVariable: 'GIT_USERNAME',
        passwordVariable: 'GIT_PASSWORD']]) {

      def appUrl = "${product}-${app}-${env}"
      steps.sh("git remote add ${defaultRemote}-${env} \"https://${steps.env.GIT_USERNAME}:${steps.env.GIT_PASSWORD}@${appUrl}.scm.${hostingEnv}.p.azurewebsites.net/${appUrl}.git\"")
      steps.sh("git checkout ${steps.env.BRANCH_NAME}")

      steps.sh("mkdir .tmp_azure_jenkings")
      steps.sh("cp ${jarPath} .tmp_azure_jenkings")
      steps.sh("cp  ${springConfigPath} .tmp_azure_jenkings")
      steps.sh("cp ${iisWebConfig} .tmp_azure_jenkings")
      steps.sh("GLOBIGNORE='.tmp_azure_jenkings:.git'; rm -rf *")
      steps.sh("cp .tmp_azure_jenkings/* .")
      steps.sh("rm -rf .tmp_azure_jenkings")
      steps.sh("git add --all .")

      steps.sh("git config user.email 'jenkinsmoj@contino.io'")
      steps.sh("git config user.name 'jenkinsmoj'")
      steps.sh("git commit -m 'Deploying ${steps.env.BUILD_NUMBER}'")
      steps.sh("git push ${defaultRemote}-${env}  master -f")
    }
  }
}
