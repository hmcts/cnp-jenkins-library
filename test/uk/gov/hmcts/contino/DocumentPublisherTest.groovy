package uk.gov.hmcts.contino

import spock.lang.Specification

import static net.javacrumbs.jsonunit.fluent.JsonFluentAssert.assertThatJson


class DocumentPublisherTest extends Specification {

  private static final String PRODUCT            = 'some-webapp'
  private static final String COMPONENT          = 'backend'
  private static final String ENVIRONMENT        = 'dev'
  private static final String BRANCH_NAME        = 'master'
  private static final String BUILD_NUMBER       = '66'
  private static final String BUILD_ID           = '12'
  private static final String BUILD_DISPLAY_NAME = 'build-display-name'
  private static final String JOB_NAME           = 'job-name'
  private static final String JOB_BASE_NAME      = 'job-base-name'
  private static final String BUILD_TAG          = 'build-tag'
  private static final String NODE_NAME          = 'node-name'
  private static final String SUBSCRIPTION       = 'prod'

  private static final String DATA = "{\"simulation\": \"RhubarbReferenceSimulation\"}"

  DocumentPublisher documentPublisher
  def steps = Mock(JenkinsStepMock)
  def params = [product: PRODUCT, component: COMPONENT, environment: ENVIRONMENT, subscription: SUBSCRIPTION]

  def setup() {
    steps.env >> [BRANCH_NAME: BRANCH_NAME,
                  BUILD_NUMBER: BUILD_NUMBER,
                  BUILD_ID: BUILD_ID,
                  BUILD_DISPLAY_NAME: BUILD_DISPLAY_NAME,
                  JOB_NAME: JOB_NAME,
                  JOB_BASE_NAME: JOB_BASE_NAME,
                  BUILD_TAG: BUILD_TAG,
                  NODE_NAME: NODE_NAME]

    documentPublisher = new DocumentPublisher(steps, params)
  }

  def "WrapWithBuildInfo"() {
    when:
      def result = documentPublisher.wrapWithBuildInfo('test.json', DATA)
    then:
      assertThatJson(result).node('product').isStringEqualTo(PRODUCT)
      assertThatJson(result).node('component').isStringEqualTo(COMPONENT)
      assertThatJson(result).node('environment').isStringEqualTo(ENVIRONMENT)
      assertThatJson(result).node('branch_name').isStringEqualTo(BRANCH_NAME)
      assertThatJson(result).node('build_number').isStringEqualTo(BUILD_NUMBER)
      assertThatJson(result).node('build_id').isStringEqualTo(BUILD_ID)
      assertThatJson(result).node('build_display_name').isStringEqualTo(BUILD_DISPLAY_NAME)
      assertThatJson(result).node('job_name').isStringEqualTo(JOB_NAME)
      assertThatJson(result).node('job_base_name').isStringEqualTo(JOB_BASE_NAME)
      assertThatJson(result).node('build_tag').isStringEqualTo(BUILD_TAG)
      assertThatJson(result).node('node_name').isStringEqualTo(NODE_NAME)

      assertThatJson(result).node('stage_timestamp').isPresent()
      assertThatJson(result).node("test\\.json").isPresent()
  }

}
