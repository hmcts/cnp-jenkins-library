#!groovy
properties(
  [[$class: 'GithubProjectProperty', projectUrlStr: 'http://git.reform/contino/jenkins-library/'],
   pipelineTriggers([
     [$class: 'GitHubPushTrigger'],
     [$class: 'hudson.triggers.TimerTrigger', spec  : 'H 1 * * *']
   ])]
)

node {
  try {
    stage('Checkout') {
      deleteDir()
      checkout scm
    }

    stage('Build') {
      sh "./gradlew clean build -x test"
    }

    stage('Test') {
      sh "./gradlew test"
    }
  } catch (err) {
    slackSend(
      channel: '#contino',
      color: 'danger',
      message: "${env.JOB_NAME}: <${env.BUILD_URL}console|Build ${env.BUILD_DISPLAY_NAME}> has FAILED")
    throw err
  }
}
