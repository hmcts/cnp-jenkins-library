package uk.gov.hmcts.contino

import uk.gov.hmcts.pipeline.SonarProperties

class RubyBuilder extends AbstractBuilder {

  def product

  RubyBuilder(steps) {
    super(steps)
  }

  def build() {
    addVersionInfo()
    bundle("config set path 'vendor/bundle'")
    bundle("install --jobs=4 --retry=3")
  }

  def fortifyScan() {
    try {
      bundle("exec rake fortify_scan")
    } finally {
      steps.archiveArtifacts allowEmptyArchive: true, artifacts: 'Fortify Scan/**'
    }
  }

  def test() {
    try {
      bundle("exec rake test")
    } finally {
      steps.junit 'tmp/test/rspec.xml'
    }
  }

  def sonarScan() {
      String properties = SonarProperties.get(steps)
      steps.sh "sonar-scanner ${properties}"
  }

  def highLevelDataSetup(String dataSetupEnvironment) {
    bundle("exec rake highLevelDataSetup --args=${dataSetupEnvironment}")
  }

  def smokeTest() {
    bundle("exec rake test:smoke")
  }

  def functionalTest() {
    bundle("exec rake test:functional")
  }

  def e2eTest() {
    bundle("exec rake test:e2e")
  }

  def apiGatewayTest() {
    bundle("exec rake test:apiGateway")
  }

  def crossBrowserTest() {
    try {
      // By default ruby will skip task execution if it's already been run (is 'up to date').
      // --rerun-tasks ensures that subsequent calls to tests against different slots are executed.
      steps.withSauceConnect("reform_tunnel") {
        bundle("exec rake test:crossbrowser")
      }
    } finally {
      steps.saucePublisher()
    }
  }

  def crossBrowserTest(String browser) {
    steps.error "Not implemented"
  }

  def mutationTest() {
    steps.error "Not implemented"
  }

  def securityCheck() {
    try {
      // TODO send to cosmos db
      bundle("exec bundle-audit check --format junit --output tmp/audit.xml")
    } finally {
      // formatter doesn't include a test if everything is passing
      steps.junit allowEmptyResults: true, testResults: 'tmp/audit.xml'
    }
  }

  @Override
  def addVersionInfo() {}

  def runProviderVerification(pactBrokerUrl, version, publish) {
    steps.echo "Not implemented runProviderVerification"
  }

  def runConsumerTests(pactBrokerUrl, version) {
    steps.echo "Not implemented runConsumerTests"
  }

  def runConsumerCanIDeploy() {
    steps.echo "Not implemented runConsumerCanIDeploy"
  }


  def bundle(String task) {
    steps.sh(script: """#!/bin/bash -l
      set +x
      source /usr/local/rvm/scripts/rvm
      rvm use
      set -x

      bundle ${task}
      """, label: "bundle ${task}")
  }

  def fullFunctionalTest() {
    functionalTest()
  }

  def dbMigrate(String vaultName, String microserviceName) {
    def secrets = [
      [ secretType: 'Secret', name: "${microserviceName}-postgres-url", version: '', envVariable: 'DATABASE_URL' ],
    ]

    def azureKeyVaultURL = "https://${vaultName}.vault.azure.net"
    steps.azureKeyVault(secrets: secrets, keyVaultURL: azureKeyVaultURL) {
      bundle("exec rake db:create db:migrate")
    }
  }

  @Override
  def setupToolVersion() {
  }

  @Override
  def performanceTest() {
    bundle("exec rake test:performance")
  }

}
