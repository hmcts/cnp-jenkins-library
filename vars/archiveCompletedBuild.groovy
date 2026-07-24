def call(Map params = [:]) {
  def sourceBuildUrl = required(params, 'sourceBuildUrl')
  def sourceJobName = required(params, 'sourceJobName')
  def sourceBuildNumber = required(params, 'sourceBuildNumber')
  def localOnly = params.localOnly == true || env.BUILD_ARCHIVE_LOCAL_ONLY == 'true'

  def jenkinsCredentialsId = params.jenkinsCredentialsId ?: env.BUILD_ARCHIVE_JENKINS_CREDENTIALS_ID
  if (!jenkinsCredentialsId && !localOnly) {
    error('A Jenkins API credential is required in BUILD_ARCHIVE_JENKINS_CREDENTIALS_ID')
  }

  validateBuildUrl(sourceBuildUrl)
  validateBuildIdentity(sourceBuildUrl, sourceJobName, sourceBuildNumber)

  def buildApiUrl = apiBuildUrl(sourceBuildUrl)
  def storageSubscription = params.storageSubscription ?: env.BUILD_ARCHIVE_STORAGE_SUBSCRIPTION ?: 'sandbox'
  def storageCredentialsId = params.storageCredentialsId ?: env.BUILD_ARCHIVE_STORAGE_CREDENTIALS_ID ?: 'buildlog-storage-account'
  def storageContainer = params.storageContainer ?: env.BUILD_ARCHIVE_STORAGE_CONTAINER ?: 'jenkins-build-archive'
  def storagePrefix = params.storagePrefix ?: env.BUILD_ARCHIVE_STORAGE_PREFIX ?: 'builds'

  node(env.BUILD_ARCHIVE_AGENT ?: '') {
    try {
      deleteDir()

      def buildMetadata = waitForBuildCompletion(buildApiUrl, jenkinsCredentialsId)
      def buildDetails = readJSON(text: buildMetadata)
      def buildResult = (buildDetails.result ?: params.sourceBuildResult ?: 'UNKNOWN').toString().toUpperCase()
      def workflowMetadata = buildResult == 'SUCCESS'
        ? null
        : getWorkflowMetadata(buildApiUrl, jenkinsCredentialsId)
      def failedStage = findFailedStage(workflowMetadata)
      def archiveName = archiveName(sourceBuildNumber, buildResult, failedStage)
      def destination = "${storageContainer}/${storagePrefix}/${safeJobPath(sourceJobName)}"

      dir(archiveName) {
        writeFile(file: 'build.json', text: buildMetadata)

        downloadRequired(
          "${buildApiUrl}consoleText",
          'console.txt',
          jenkinsCredentialsId
        )

        downloadOptional(
          "${buildApiUrl}artifact/*zip*/archive.zip",
          'artifacts.zip',
          jenkinsCredentialsId
        )

        downloadOptional(
          "${buildApiUrl}testReport/api/json",
          'test-results.json',
          jenkinsCredentialsId
        )

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
    } finally {
      deleteDir()
    }
  }
}

private Map getWorkflowMetadata(String sourceBuildUrl, String credentialsId) {
  try {
    def request = [
      httpMode: 'GET',
      quiet: true,
      url: "${sourceBuildUrl}wfapi/describe",
      validResponseCodes: '200,404'
    ]
    addAuthentication(request, credentialsId)
    def response = httpRequest(request)

    response.status == 200 ? readJSON(text: response.content) as Map : null
  } catch (err) {
    echo "Unable to determine failed build stage: ${err.message}"
    null
  }
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

private String waitForBuildCompletion(String sourceBuildUrl, String credentialsId) {
  def completedBuildMetadata = null

  timeout(time: 30, unit: 'MINUTES') {
    waitUntil(initialRecurrencePeriod: 5000) {
      def request = [
        httpMode: 'GET',
        quiet: true,
        url: "${sourceBuildUrl}api/json",
        validResponseCodes: '200'
      ]
      addAuthentication(request, credentialsId)
      def response = httpRequest(request)
      def metadata = readJSON(text: response.content)

      if (metadata.building) {
        return false
      }

      completedBuildMetadata = response.content
      return true
    }
  }

  completedBuildMetadata
}

private void downloadRequired(String url, String outputFile, String credentialsId) {
  def request = [
    httpMode: 'GET',
    outputFile: outputFile,
    quiet: true,
    url: url,
    validResponseCodes: '200'
  ]
  addAuthentication(request, credentialsId)
  httpRequest(request)
}

private void downloadOptional(String url, String outputFile, String credentialsId) {
  def request = [
    httpMode: 'GET',
    outputFile: outputFile,
    quiet: true,
    url: url,
    validResponseCodes: '200,404'
  ]
  addAuthentication(request, credentialsId)
  def response = httpRequest(request)

  if (response.status == 404) {
    sh(label: "Remove missing ${outputFile}", script: "rm -f '${outputFile}'")
  }
}

private void addAuthentication(Map request, String credentialsId) {
  if (credentialsId) {
    request.authentication = credentialsId
  }
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
  if (!jenkinsUrl || !sourceBuildUrl.startsWith(jenkinsUrl) || !(sourceBuildUrl ==~ /https?:\/\/[^\/]+\/job\/.+\/\d+\/$/)) {
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
    error("Refusing to archive mismatched Jenkins build details")
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
    error("Refusing to archive mismatched Jenkins build details")
  }
}

private String apiBuildUrl(String sourceBuildUrl) {
  def apiBaseUrl = env.BUILD_ARCHIVE_JENKINS_API_URL
  if (!apiBaseUrl) {
    return sourceBuildUrl
  }

  if (!(apiBaseUrl ==~ /https?:\/\/[^\/]+(?::\d+)?\/$/)) {
    error("Refusing to use an invalid Jenkins API base URL: ${apiBaseUrl}")
  }

  "${apiBaseUrl}${sourceBuildUrl.substring(env.JENKINS_URL.length())}"
}

private String safeJobPath(String sourceJobName) {
  sourceJobName
    .tokenize('/')
    .collect { segment -> segment.replaceAll(/[^A-Za-z0-9._-]/, '_') }
    .join('/')
}
