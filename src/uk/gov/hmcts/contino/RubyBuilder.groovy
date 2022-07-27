package uk.gov.hmcts.contino

import groovy.json.JsonSlurper
import uk.gov.hmcts.pipeline.CVEPublisher
import uk.gov.hmcts.pipeline.SonarProperties
import uk.gov.hmcts.pipeline.deprecation.WarningCollector

import java.time.LocalDate

class RubyBuilder extends AbstractBuilder {

  def product

    RubyBuilder(steps, product) {
    super(steps)
    this.product = product
  }

  def build() {
    addVersionInfo()
    bundle("install --jobs=4 --retry=3 --path vendor/bundle")
  }

  def fortifyScan() {
    bundle("exec rake fortifyScan")
  }

  def test() {
    try {
      bundle("exec rake test")
    } finally {
      steps.junit '**/test-results/test/*.xml'
      steps.archiveArtifacts artifacts: '**/reports/checkstyle/*.html', allowEmptyArchive: true
    }
  }

  def sonarScan() {
    steps.error "Not implemented"
//      String properties = SonarProperties.get(steps)

      // TODO
      // bundle("--info ${properties} sonarqube")
  }

  def highLevelDataSetup(String dataSetupEnvironment) {
    bundle("exec rake highLevelDataSetup --args=${dataSetupEnvironment}")
  }

  def smokeTest() {
    try {
      // By default ruby will skip task execution if it's already been run (is 'up to date').
      // --rerun-tasks ensures that subsequent calls to tests against different slots are executed.
      bundle("exec rake smoke")
    } finally {
      try {
        steps.junit '**/test-results/smoke/*.xml,**/test-results/smokeTest/*.xml'
      } catch (ignored) {
        WarningCollector.addPipelineWarning("deprecated_smoke_test_archiving", "No smoke  test results found, make sure you have at least one created.", LocalDate.of(2022, 6, 30))
      }
    }
  }

  def functionalTest() {
    try {
      // By default ruby will skip task execution if it's already been run (is 'up to date').
      // --rerun-tasks ensures that subsequent calls to tests against different slots are executed.
      bundle("exec rake test:functional")
    } finally {
      try {
        steps.junit '**/test-results/functional/*.xml,**/test-results/functionalTest/*.xml'
      } catch (ignored) {
        WarningCollector.addPipelineWarning("deprecated_functional_test_archiving", "No functional test results found, make sure you have at least one created.", LocalDate.of(2022, 6, 30))
      }
    }
  }

  def apiGatewayTest() {
    try {
      // By default ruby will skip task execution if it's already been run (is 'up to date').
      // --rerun-tasks ensures that subsequent calls to tests against different slots are executed.
      bundle("exec rake test:apiGateway")
    } finally {
      try {
        steps.junit '**/test-results/api/*.xml,**/test-results/apiTest/*.xml'
      } catch (ignored) {
        WarningCollector.addPipelineWarning("deprecated_apiGateway_test_archiving", "No API gateway test results found, make sure you have at least one created.", LocalDate.of(2022, 6, 30))
      }
    }
  }

  def crossBrowserTest() {
    try {
      // By default ruby will skip task execution if it's already been run (is 'up to date').
      // --rerun-tasks ensures that subsequent calls to tests against different slots are executed.
      steps.withSauceConnect("reform_tunnel") {
        bundle("exec rake test:crossbrowser")
      }
    } finally {
      steps.archiveArtifacts allowEmptyArchive: true, artifacts: 'functional-output/**/*'
      steps.saucePublisher()
    }
  }

  def crossBrowserTest(String browser) {
    steps.error "Not implemented"
  }

  def mutationTest(){
    steps.error "Not implemented"
  }

  def securityCheck() {
    steps.error "Not implemented"
  }

  @Override
  def addVersionInfo() {
    // TODO look at this
  }

  def runProviderVerification(pactBrokerUrl, version, publish) {
    steps.error "Not implemented"
  }

  def runConsumerTests(pactBrokerUrl, version) {
    steps.error "Not implemented"
  }

  def runConsumerCanIDeploy() {
    steps.error "Not implemented"
  }


  def bundle(String task) {
    steps.sh """#!/bin/bash -l
      set +x
      source /usr/local/rvm/scripts/rvm
      rvm use
      set -x

      bundle ${task}
      """
  }

  def fullFunctionalTest() {
      functionalTest()
  }

  def dbMigrate(String vaultName, String microserviceName) {
    steps.error "Not implemented"
  }

  @Override
  def setupToolVersion() {
  }

  @Override
  def performanceTest() {
    steps.error "Not implemented"
  }

}
