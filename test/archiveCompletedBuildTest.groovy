import com.lesfurets.jenkins.unit.BasePipelineTest
import hudson.FilePath
import hudson.model.Result
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import org.junit.Before
import org.junit.Test

import static org.assertj.core.api.Assertions.assertThat

class archiveCompletedBuildTest extends BasePipelineTest {

  def archived
  def timeouts = []
  def uploads = []
  def writes = [:]
  def buildReader
  def workspace
  def script

  @Override
  @Before
  void setUp() {
    super.setUp()
    binding.setVariable('env', [
      JENKINS_URL: 'https://build.example/',
      BUILD_ARCHIVE_LOCAL_ONLY: 'true',
      BUILD_ARCHIVE_AGENT: ''
    ])

    buildReader = new FakeCompletedBuildReader()
    workspace = new FilePath(new File('build/archive-test-workspace'))

    helper.registerAllowedMethod('node', [String.class, Closure.class], { _, body -> body.call() })
    helper.registerAllowedMethod('deleteDir', [], {})
    helper.registerAllowedMethod('dir', [String.class, Closure.class], { _, body -> body.call() })
    helper.registerAllowedMethod('getContext', [Class.class], { workspace })
    helper.registerAllowedMethod('timeout', [Map.class, Closure.class], { options, body ->
      timeouts << options
      body.call()
    })
    helper.registerAllowedMethod('waitUntil', [Map.class, Closure.class], { _, body ->
      while (!body.call()) {
        // Repeat until the mocked build reports completion.
      }
    })
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
  void copiesCompletedBuildOutputsWithoutAnApiCredential() {
    binding.getVariable('env').BUILD_ARCHIVE_WAIT_TIMEOUT_MINUTES = '301'
    binding.getVariable('env').BUILD_ARCHIVE_OPERATION_TIMEOUT_MINUTES = '121'
    buildReader.snapshots = [
      [building: true],
      completedBuild()
    ]

    script.call(
      sourceBuildUrl: 'https://build.example/job/service/job/PR-1/4/',
      sourceJobName: 'service/PR-1',
      sourceBuildNumber: '4',
      sourceBuildResult: 'SUCCESS',
      sourceProduct: 'et',
      sourceComponent: 'cos',
      buildReader: buildReader
    )

    assertThat(buildReader.snapshotRequests).containsExactly(
      ['service/PR-1', 4],
      ['service/PR-1', 4]
    )
    assertThat(buildReader.copyRequests).hasSize(1)
    assertThat(buildReader.copyRequests[0].take(2)).containsExactly('service/PR-1', 4)
    assertThat(timeouts*.time).containsExactly(301, 121)
    assertThat(writes['build.json'].result).isEqualTo('FAILURE')
    assertThat(writes['build.json']).doesNotContainKeys('workflow', 'tests')
    assertThat(writes['test-results.json'].failCount).isEqualTo(2)
    assertThat(writes['archive-metadata.json'].sourceJobName).isEqualTo('service/PR-1')
    assertThat(writes['archive-metadata.json'].archivedAt).isEqualTo('2026-07-23T14:30:00Z')
    assertThat(archived.artifacts.toString())
      .isEqualTo('completed-build_4_FAILURE_Deploy_to_AKS_Preview/**')
    assertThat(uploads).isEmpty()
  }

  @Test
  void uploadsToTheSandboxStorageSubscriptionByDefault() {
    binding.getVariable('env').BUILD_ARCHIVE_LOCAL_ONLY = 'false'
    buildReader.snapshots = [completedBuild(workflow: null, tests: null)]

    script.call(
      sourceBuildUrl: 'https://build.example/job/service/job/PR-1/4/',
      sourceJobName: 'service/PR-1',
      sourceBuildNumber: '4',
      buildReader: buildReader
    )

    assertThat(uploads).hasSize(1)
    assertThat(uploads[0]*.toString()).containsExactly(
      'sandbox',
      'buildlog-storage-account',
      'completed-build_4_FAILURE',
      'jenkins-build-archive/builds/service/PR-1'
    )
  }

  @Test
  void ignoresAnAbortedBuildAfterReadingItsFinalResult() {
    buildReader.snapshots = [completedBuild(result: 'ABORTED')]

    script.call(
      sourceBuildUrl: 'https://build.example/job/service/job/PR-1/4/',
      sourceJobName: 'service/PR-1',
      sourceBuildNumber: '4',
      buildReader: buildReader
    )

    assertThat(buildReader.copyRequests).isEmpty()
    assertThat(uploads).isEmpty()
    assertThat(archived).isNull()
  }

  @Test
  void preservesAnInterruptionWhileReadingTheCompletedBuild() {
    def interruption = new FlowInterruptedException(Result.ABORTED, true)
    buildReader.failure = interruption

    try {
      script.call(
        sourceBuildUrl: 'https://build.example/job/service/job/PR-1/4/',
        sourceJobName: 'service/PR-1',
        sourceBuildNumber: '4',
        buildReader: buildReader
      )
    } catch (FlowInterruptedException expected) {
      assertThat(expected).isSameAs(interruption)
      return
    }

    throw new AssertionError('Expected the completed build interruption to propagate')
  }

  @Test
  void rejectsBuildUrlsOutsideTheConfiguredJenkins() {
    try {
      script.call(
        sourceBuildUrl: 'https://malicious.example/job/service/4/',
        sourceJobName: 'service',
        sourceBuildNumber: '4',
        buildReader: buildReader
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

  private static Map completedBuild(Map overrides = [:]) {
    [
      building: false,
      result: 'FAILURE',
      number: 4,
      url: 'job/service/job/PR-1/4/',
      displayName: '#4',
      fullDisplayName: 'service/PR-1 #4',
      timestamp: 1785240000000,
      duration: 5000,
      estimatedDuration: 4000,
      workflow: [
        stages: [
          [name: 'Build and test', status: 'SUCCESS'],
          [name: 'Deploy to AKS / Preview', status: 'FAILED']
        ]
      ],
      tests: [
        totalCount: 10,
        failCount: 2,
        skipCount: 1,
        passCount: 7
      ]
    ] + overrides
  }

  private void assertInvalidBuildIdentity(Map params) {
    try {
      script.call(
        sourceBuildUrl: params.sourceBuildUrl,
        sourceJobName: params.sourceJobName,
        sourceBuildNumber: params.sourceBuildNumber,
        buildReader: buildReader
      )
    } catch (IllegalArgumentException expected) {
      assertThat(expected.message).contains(params.expectedMessage)
      return
    }

    throw new AssertionError("Expected build archive validation to reject ${params}")
  }
}

class FakeCompletedBuildReader implements Serializable {

  private static final long serialVersionUID = 1L

  List<Map> snapshots = []
  List snapshotRequests = []
  List copyRequests = []
  Throwable failure

  Map snapshot(String jobName, int buildNumber) {
    snapshotRequests << [jobName, buildNumber]
    if (failure) {
      throw failure
    }
    snapshots.remove(0)
  }

  void copyOutputs(String jobName, int buildNumber, FilePath workspace) {
    copyRequests << [jobName, buildNumber, workspace]
  }
}
