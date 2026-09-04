import hudson.FilePath
import uk.gov.hmcts.contino.CompletedBuildReader

def call(Map params = [:]) {
  def sourceBuildUrl = required(params, 'sourceBuildUrl')
  def sourceJobName = required(params, 'sourceJobName')
  def sourceBuildNumber = required(params, 'sourceBuildNumber')
  def localOnly = params.localOnly == true || env.BUILD_ARCHIVE_LOCAL_ONLY == 'true'
  def buildReader = params.buildReader ?: new CompletedBuildReader()

  validateBuildUrl(sourceBuildUrl)
  validateBuildIdentity(sourceBuildUrl, sourceJobName, sourceBuildNumber)

  def storageSubscription = params.storageSubscription ?: env.BUILD_ARCHIVE_STORAGE_SUBSCRIPTION ?: 'sandbox'
  def storageCredentialsId = params.storageCredentialsId ?: env.BUILD_ARCHIVE_STORAGE_CREDENTIALS_ID ?:
    'buildlog-storage-account'
  def storageContainer = params.storageContainer ?: env.BUILD_ARCHIVE_STORAGE_CONTAINER ?: 'jenkins-build-archive'
  def storagePrefix = params.storagePrefix ?: env.BUILD_ARCHIVE_STORAGE_PREFIX ?: 'builds'
  def waitTimeoutMinutes = configuredTimeout(
    env.BUILD_ARCHIVE_WAIT_TIMEOUT_MINUTES,
    300,
    'BUILD_ARCHIVE_WAIT_TIMEOUT_MINUTES'
  )
  def operationTimeoutMinutes = configuredTimeout(
    env.BUILD_ARCHIVE_OPERATION_TIMEOUT_MINUTES,
    120,
    'BUILD_ARCHIVE_OPERATION_TIMEOUT_MINUTES'
  )

  node(env.BUILD_ARCHIVE_AGENT ?: '') {
    try {
      deleteDir()

      def buildDetails = waitForBuildCompletion(
        buildReader,
        sourceJobName,
        sourceBuildNumber,
        waitTimeoutMinutes
      )
      def buildResult = (buildDetails.result ?: params.sourceBuildResult ?: 'UNKNOWN').toString().toUpperCase()
      if (buildResult != 'FAILURE') {
        echo "Skipping build archive because final result is ${buildResult}"
        return
      }

      timeout(time: operationTimeoutMinutes, unit: 'MINUTES') {
        def workflowMetadata = buildDetails.workflow as Map
        def failedStage = findFailedStage(workflowMetadata)
        def archiveName = archiveName(sourceBuildNumber, buildResult, failedStage)
        def destination = "${storageContainer}/${storagePrefix}/${safeJobPath(sourceJobName)}"

        dir(archiveName) {
          writeJSON(
            file: 'build.json',
            json: buildDetails.findAll { key, _ -> !(key in ['workflow', 'tests']) },
            pretty: 2
          )

          buildReader.copyOutputs(
            sourceJobName,
            sourceBuildNumber.toInteger(),
            getContext(FilePath)
          )

          if (buildDetails.tests) {
            writeJSON(
              file: 'test-results.json',
              json: buildDetails.tests,
              pretty: 2
            )
          }

          if (workflowMetadata) {
            writeJSON(
              file: 'workflow.json',
              json: workflowMetadata,
              pretty: 2
            )
          }

          writeJSON(
            file: 'archive-metadata.json',
            json: [
              sourceBuildUrl: sourceBuildUrl,
              sourceJobName: sourceJobName,
              sourceBuildNumber: sourceBuildNumber,
              sourceBuildResult: buildResult,
              failedStage: failedStage ?: '',
              sourceProduct: params.sourceProduct ?: '',
              sourceComponent: params.sourceComponent ?: '',
              archivedAt: sh(
                script: "date -u '+%Y-%m-%dT%H:%M:%SZ'",
                returnStdout: true
              ).trim()
            ],
            pretty: 2
          )
        }

        if (localOnly) {
          archiveArtifacts(
            allowEmptyArchive: false,
            artifacts: "${archiveName}/**"
          )
          echo "Archived ${sourceJobName} #${sourceBuildNumber} in the local Jenkins archive"
        } else {
          azureBlobUpload(
            storageSubscription,
            storageCredentialsId,
            archiveName,
            destination
          )
          echo "Archived ${sourceJobName} #${sourceBuildNumber} to ${destination}"
        }
      }
    } finally {
      deleteDir()
    }
  }
}

private Map waitForBuildCompletion(
  def buildReader,
  String sourceJobName,
  String sourceBuildNumber,
  int waitTimeoutMinutes
) {
  def completedBuildMetadata = null

  timeout(time: waitTimeoutMinutes, unit: 'MINUTES') {
    waitUntil(initialRecurrencePeriod: 5000) {
      def metadata = buildReader.snapshot(sourceJobName, sourceBuildNumber.toInteger())
      if (metadata.building) {
        return false
      }

      completedBuildMetadata = metadata
      return true
    }
  }

  completedBuildMetadata
}

private String findFailedStage(Map workflowMetadata) {
  def failedStage = workflowMetadata?.stages?.find { stage ->
    stage.status?.toString()?.toUpperCase() in ['FAILED', 'FAILURE', 'UNSTABLE', 'ABORTED']
  }

  failedStage?.name?.toString()
}

private String archiveName(String buildNumber, String buildResult, String failedStage) {
  def parts = ['completed-build', safeFileNamePart(buildNumber), safeFileNamePart(buildResult)]
  if (failedStage) {
    parts << safeFileNamePart(failedStage)
  }
  parts.join('_')
}

private String safeFileNamePart(String value) {
  value
    .replaceAll(/[^A-Za-z0-9.-]+/, '_')
    .replaceAll(/^_+|_+$/, '')
    .take(100) ?: 'unknown'
}

private int configuredTimeout(def value, int defaultValue, String variableName) {
  def configuredValue = value ?: defaultValue.toString()
  if (!(configuredValue.toString() ==~ /\d+/) || configuredValue.toString().toInteger() < 1) {
    error("${variableName} must be a positive integer")
  }
  configuredValue.toString().toInteger()
}

private String required(Map params, String name) {
  def value = params[name]
  if (!value) {
    error("Missing required build archive parameter: ${name}")
  }
  value.toString()
}

private void validateBuildUrl(String sourceBuildUrl) {
  def jenkinsUrl = env.JENKINS_URL
  if (!jenkinsUrl || !sourceBuildUrl.startsWith(jenkinsUrl) ||
    !(sourceBuildUrl ==~ /https?:\/\/[^\/]+\/job\/.+\/\d+\/$/)) {
    error("Refusing to archive an invalid Jenkins build URL: ${sourceBuildUrl}")
  }
}

private void validateBuildIdentity(String sourceBuildUrl, String sourceJobName, String sourceBuildNumber) {
  if (!(sourceBuildNumber ==~ /\d+/)) {
    error("Refusing to archive an invalid Jenkins build number: ${sourceBuildNumber}")
  }

  def relativeBuildUrl = sourceBuildUrl.substring(env.JENKINS_URL.length())
  def buildUrlParts = relativeBuildUrl =~ /^job\/(.+)\/(\d+)\/$/
  def buildNumberFromUrl = buildUrlParts[0][2]
  if (buildNumberFromUrl != sourceBuildNumber) {
    error('Refusing to archive mismatched Jenkins build details')
  }

  def jobSegments = sourceJobName.split('/', -1)
  if (jobSegments.any { segment -> !segment || segment in ['.', '..'] }) {
    error("Refusing to archive an invalid Jenkins job name: ${sourceJobName}")
  }

  def jobNameFromUrl = buildUrlParts[0][1]
    .split('/job/', -1)
    .collect { segment -> new URI("https://jenkins.invalid/${segment}").path.substring(1) }
    .join('/')
  if (jobNameFromUrl != sourceJobName) {
    error('Refusing to archive mismatched Jenkins build details')
  }
}

private String safeJobPath(String sourceJobName) {
  sourceJobName
    .tokenize('/')
    .collect { segment -> segment.replaceAll(/[^A-Za-z0-9._-]/, '_') }
    .join('/')
}
