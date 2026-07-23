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

  def buildApiUrl = apiBuildUrl(sourceBuildUrl)
  def storageSubscription = params.storageSubscription ?: env.BUILD_ARCHIVE_STORAGE_SUBSCRIPTION ?: 'DCD-CFT-Sandbox'
  def storageCredentialsId = params.storageCredentialsId ?: env.BUILD_ARCHIVE_STORAGE_CREDENTIALS_ID ?: 'buildlog-storage-account'
  def storageContainer = params.storageContainer ?: env.BUILD_ARCHIVE_STORAGE_CONTAINER ?: 'jenkins-build-archive'
  def storagePrefix = params.storagePrefix ?: env.BUILD_ARCHIVE_STORAGE_PREFIX ?: 'builds'
  def archiveDirectory = "completed-build-${sourceBuildNumber}"
  def destination = "${storageContainer}/${storagePrefix}/${safeJobPath(sourceJobName)}/${sourceBuildNumber}"

  node(env.BUILD_ARCHIVE_AGENT ?: '') {
    try {
      deleteDir()

      def buildMetadata = waitForBuildCompletion(buildApiUrl, jenkinsCredentialsId)

      dir(archiveDirectory) {
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

        writeJSON(
          file: 'archive-metadata.json',
          json: [
            sourceBuildUrl: sourceBuildUrl,
            sourceJobName: sourceJobName,
            sourceBuildNumber: sourceBuildNumber,
            sourceBuildResult: params.sourceBuildResult ?: '',
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
          artifacts: "${archiveDirectory}/**"
        )
        echo "Archived ${sourceJobName} #${sourceBuildNumber} in the local Jenkins archive"
      } else {
        azureBlobUpload(
          storageSubscription,
          storageCredentialsId,
          archiveDirectory,
          destination
        )
        echo "Archived ${sourceJobName} #${sourceBuildNumber} to ${destination}"
      }
    } finally {
      deleteDir()
    }
  }
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
