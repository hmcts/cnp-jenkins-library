#!groovy
@Library('Infrastructure') _

def channel = '#cnp-build-status'

node {
  try {
    stage('Checkout') {
      deleteDir()
      checkout scm
    }

    stage('Build') {
      sh "./gradlew --info clean build -x test"
    }

    stage('Test') {
      sh "./gradlew --info test"
    }

    stage('Run pipeline') {
      build job: 'HMCTS_pipeline_test/moj-rhubarb-recipes-service/master',
        parameters: [string(name: 'LIB_VERSION', value: env.CHANGE_BRANCH)]

    }

  } catch (err) {
    notifyBuildFailure channel: channel
    throw err
  }
  notifyBuildFixed channel: channel
}
