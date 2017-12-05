#!groovy
properties(
  [[$class: 'GithubProjectProperty', projectUrlStr: 'http://git.reform/contino/jenkins-library/'],
   pipelineTriggers([
     [$class: 'GitHubPushTrigger'],
     [$class: 'hudson.triggers.TimerTrigger', spec  : 'H 1 * * *']
   ])]
)

@Library('Infrastructure') _

def channel = '#cnp-build-status'

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
    notifyBuildFailure channel: channel
    throw err
  }
  notifyBuildFixed channel: channel
}
