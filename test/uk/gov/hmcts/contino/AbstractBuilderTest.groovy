package uk.gov.hmcts.contino

import spock.lang.Specification

class AbstractBuilderTest extends Specification {

  def builder
  def mockSteps
  def mockGatling
  def mockSecurity

  def setup() {
    mockSteps = Mock(JenkinsStepMock.class)
    mockGatling = Mock(Gatling)
    mockSecurity = Mock(SecurityScan)
    mockSecurityFrontend = Mock(SecurityScanFrontend)
    mockSecurityBackend = Mock(SecurityScanBackend)
    builder = new BuilderImpl(mockSteps)
    builder.gatling = mockGatling
    builder.securitytest = mockSecurity
    builder.securitytestfrontend = mockSecurityFrontend
    builder.securitytestbackend = mockSecurityBackend
  }

  def "performanceTest calls 'gatling.execute()'"() {

    when:
      builder.performanceTest()
    then:
      1 * mockGatling.execute()
  }

  def "security calls 'securitytest.execute()'"() {
    when:
    builder.securityScan()
    then:
    1 * mockSecurity.execute()
  }

  def "security calls 'securitytestfrontend.execute()'"() {
    when:
    builder.securityScanFrontend()
    then:
    1 * mockSecurityFrontend.execute()
  }

  def "security calls 'securitytestbackend.execute()'"() {
    when:
    builder.securityScanBackend()
    then:
    1 * mockSecurityBackend.execute()
  }


  class BuilderImpl extends AbstractBuilder {
    BuilderImpl(steps) {
      super(steps)
    }

    @Override
    def build() {
      return null
    }

    @Override
    def fortifyScan() {
      return null
    }

    @Override
    def test() {
      return null
    }

    @Override
    def sonarScan() {
      return null
    }

    @Override
    def highLevelDataSetup(String dataSetupEnvironment) {
      return null
    }

    @Override
    def smokeTest() {
      return null
    }

    @Override
    def functionalTest() {
      return null
    }

    @Override
    def apiGatewayTest() {
      return null
    }

    @Override
    def securityCheck() {
      return null
    }

    @Override
    def crossBrowserTest() {
      return null
    }

    @Override
    def mutationTest() {
      return null
    }

    @Override
    def addVersionInfo() {
      return null
    }

    @Override
    def fullFunctionalTest() {
      return null
    }

    @Override
    def runProviderVerification() {
      return null
    }

    @Override
    def runConsumerTests() {
      return null
    }

    @Override
    def runConsumerCanIDeploy() {
      return null
    }

    @Override
    def setupToolVersion() {
      return null
    }
  }
}
