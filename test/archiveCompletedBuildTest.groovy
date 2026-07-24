import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test
import groovy.json.JsonSlurperClassic

import static org.assertj.core.api.Assertions.assertThat

class archiveCompletedBuildTest extends BasePipelineTest {

  def archived
  def downloads = []
  def uploads = []
  def writes = [:]
  def metadataChecks = 0
  def buildResult = 'SUCCESS'
  def workflowStages = []
  def script

  @Override
  @Before
  void setUp() {
    super.setUp()
    binding.setVariable('env', [
      JENKINS_URL: 'https://build.example/',
      BUILD_ARCHIVE_JENKINS_API_URL: 'http://localhost:8080/',
      BUILD_ARCHIVE_LOCAL_ONLY: 'true',
      BUILD_ARCHIVE_AGENT: ''
    ])

    helper.registerAllowedMethod('node', [String.class, Closure.class], { _, body -> body.call() })
    helper.registerAllowedMethod('deleteDir', [], {})
    helper.registerAllowedMethod('dir', [String.class, Closure.class], { _, body -> body.call() })
    helper.registerAllowedMethod('timeout', [Map.class, Closure.class], { _, body -> body.call() })
    helper.registerAllowedMethod('waitUntil', [Map.class, Closure.class], { _, body ->
      while (!body.call()) {
        // Repeat until the mocked build reports completion.
      }
    })
    helper.registerAllowedMethod('httpRequest', [Map.class], { request ->
      if (request.url.endsWith('/api/json') && !request.url.contains('testReport')) {
        metadataChecks++
        return [
          status: 200,
          content: metadataChecks == 1
            ? '{"building":true}'
            : """{"building":false,"result":"${buildResult}"}"""
        ]
      }

      if (request.url.endsWith('/wfapi/describe')) {
        return [
          status: 200,
          content: groovy.json.JsonOutput.toJson([stages: workflowStages])
        ]
      }

      downloads << request
      return [status: 200, content: '']
    })
    helper.registerAllowedMethod('readJSON', [Map.class], {
      new JsonSlurperClassic().parseText(it.text)
    })
    helper.registerAllowedMethod('writeFile', [Map.class], { writes[it.file] = it.text })
    helper.registerAllowedMethod('writeJSON', [Map.class], { writes[it.file] = it.json })
    helper.registerAllowedMethod('archiveArtifacts', [Map.class], { archived = it })
    helper.registerAllowedMethod('azureBlobUpload', [String.class, String.class, String.class, String.class], {
      uploads << it
    })
    helper.registerAllowedMethod('sh', [Map.class], {
      it.returnStdout ? '2026-07-23T14:30:00Z\n' : null
    })
    helper.registerAllowedMethod('echo', [String.class], {})
    helper.registerAllowedMethod('error', [String.class], { throw new IllegalArgumentException(it) })

    script = loadScript('vars/archiveCompletedBuild.groovy')
  }

  @Test
  void waitsForCompletionAndArchivesTheWholeBuildLocally() {
    script.call(
      sourceBuildUrl: 'https://build.example/job/service/job/PR-1/4/',
      sourceJobName: 'service/PR-1',
      sourceBuildNumber: '4',
      sourceBuildResult: 'SUCCESS',
      sourceProduct: 'et',
      sourceComponent: 'cos'
    )

    assertThat(metadataChecks).isEqualTo(2)
    assertThat(downloads*.url).containsExactlyInAnyOrder(
      'http://localhost:8080/job/service/job/PR-1/4/consoleText',
      'http://localhost:8080/job/service/job/PR-1/4/artifact/*zip*/archive.zip',
      'http://localhost:8080/job/service/job/PR-1/4/testReport/api/json'
    )
    assertThat(writes['build.json'].toString()).contains('"result":"SUCCESS"')
    assertThat(writes['archive-metadata.json'].sourceJobName).isEqualTo('service/PR-1')
    assertThat(writes['archive-metadata.json'].archivedAt).isEqualTo('2026-07-23T14:30:00Z')
    assertThat(archived.artifacts.toString()).isEqualTo('completed-build_4_SUCCESS/**')
    assertThat(uploads).isEmpty()
  }

  @Test
  void includesTheOutcomeAndFailedStageInTheArchiveName() {
    buildResult = 'FAILURE'
    workflowStages = [
      [name: 'Build and test', status: 'SUCCESS'],
      [name: 'Deploy to AKS / Preview', status: 'FAILED']
    ]

    script.call(
      sourceBuildUrl: 'https://build.example/job/service/job/PR-1/4/',
      sourceJobName: 'service/PR-1',
      sourceBuildNumber: '4',
      sourceBuildResult: 'SUCCESS',
      sourceProduct: 'et',
      sourceComponent: 'cos'
    )

    assertThat(archived.artifacts.toString())
      .isEqualTo('completed-build_4_FAILURE_Deploy_to_AKS_Preview/**')
    assertThat(writes['archive-metadata.json'].sourceBuildResult).isEqualTo('FAILURE')
    assertThat(writes['archive-metadata.json'].failedStage).isEqualTo('Deploy to AKS / Preview')
    assertThat(writes['workflow.json'].stages[1].status).isEqualTo('FAILED')
  }

  @Test
  void uploadsToTheSandboxStorageSubscriptionByDefault() {
    binding.getVariable('env').BUILD_ARCHIVE_LOCAL_ONLY = 'false'
    binding.getVariable('env').BUILD_ARCHIVE_JENKINS_CREDENTIALS_ID = 'jenkins-api'

    script.call(
      sourceBuildUrl: 'https://build.example/job/service/job/PR-1/4/',
      sourceJobName: 'service/PR-1',
      sourceBuildNumber: '4'
    )

    assertThat(uploads).hasSize(1)
    assertThat(uploads[0]*.toString()).containsExactly(
      'sandbox',
      'buildlog-storage-account',
      'completed-build_4_SUCCESS',
      'jenkins-build-archive/builds/service/PR-1/completed-build_4_SUCCESS'
    )
  }

  @Test
  void rejectsBuildUrlsOutsideTheConfiguredJenkins() {
    try {
      script.call(
        sourceBuildUrl: 'https://malicious.example/job/service/4/',
        sourceJobName: 'service',
        sourceBuildNumber: '4'
      )
    } catch (IllegalArgumentException expected) {
      assertThat(expected.message).contains('invalid Jenkins build URL')
      return
    }

    throw new AssertionError('Expected an invalid build URL to be rejected')
  }

  @Test
  void rejectsNonNumericBuildNumbers() {
    assertInvalidBuildIdentity(
      sourceBuildUrl: 'https://build.example/job/service/4/',
      sourceJobName: 'service',
      sourceBuildNumber: '../4',
      expectedMessage: 'invalid Jenkins build number'
    )
  }

  @Test
  void rejectsBuildNumbersThatDoNotMatchTheBuildUrl() {
    assertInvalidBuildIdentity(
      sourceBuildUrl: 'https://build.example/job/service/4/',
      sourceJobName: 'service',
      sourceBuildNumber: '5',
      expectedMessage: 'mismatched Jenkins build details'
    )
  }

  @Test
  void rejectsJobNamesThatDoNotMatchTheBuildUrl() {
    assertInvalidBuildIdentity(
      sourceBuildUrl: 'https://build.example/job/service/job/PR-1/4/',
      sourceJobName: 'different-service/PR-1',
      sourceBuildNumber: '4',
      expectedMessage: 'mismatched Jenkins build details'
    )
  }

  @Test
  void rejectsUnsafeJobPathSegments() {
    assertInvalidBuildIdentity(
      sourceBuildUrl: 'https://build.example/job/service/4/',
      sourceJobName: '../service',
      sourceBuildNumber: '4',
      expectedMessage: 'invalid Jenkins job name'
    )
  }

  private void assertInvalidBuildIdentity(Map params) {
    try {
      script.call(
        sourceBuildUrl: params.sourceBuildUrl,
        sourceJobName: params.sourceJobName,
        sourceBuildNumber: params.sourceBuildNumber
      )
    } catch (IllegalArgumentException expected) {
      assertThat(expected.message).contains(params.expectedMessage)
      return
    }

    throw new AssertionError("Expected build archive validation to reject ${params}")
  }
}
