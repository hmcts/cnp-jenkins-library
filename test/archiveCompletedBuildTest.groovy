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
            : '{"building":false,"result":"SUCCESS"}'
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
    assertThat(archived.artifacts.toString()).isEqualTo('completed-build-4/**')
    assertThat(uploads).isEmpty()
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
}
