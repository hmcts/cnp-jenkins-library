def call(Map params = [:]) {
  def archiveJob = '/Archive Completed Builds'
  def buildResult = currentBuild.result ?: currentBuild.currentResult ?: 'SUCCESS'

  if (buildResult != 'FAILURE') {
    return
  }

  if (env.BUILD_ARCHIVE_QUEUED == 'true') {
    echo "Skipping duplicate build archive trigger"
    return
  }

  if (env.JOB_NAME == archiveJob.substring(1)) {
    echo "Skipping build archive trigger for the archive job itself"
    return
  }

  if (!env.BUILD_URL || !env.JOB_NAME || !env.BUILD_NUMBER) {
    echo "Skipping build archive because Jenkins build metadata is incomplete"
    return
  }

  try {
    build(
      job: archiveJob,
      wait: false,
      propagate: false,
      parameters: [
        string(name: 'SOURCE_BUILD_URL', value: env.BUILD_URL),
        string(name: 'SOURCE_JOB_NAME', value: env.JOB_NAME),
        string(name: 'SOURCE_BUILD_NUMBER', value: env.BUILD_NUMBER),
        string(name: 'SOURCE_BUILD_RESULT', value: buildResult),
        string(name: 'SOURCE_PRODUCT', value: params.product ?: ''),
        string(name: 'SOURCE_COMPONENT', value: params.component ?: '')
      ]
    )
    env.BUILD_ARCHIVE_QUEUED = 'true'
    echo "Queued build archive for ${env.JOB_NAME} #${env.BUILD_NUMBER}"
  } catch (err) {
    echo "Unable to queue build archive: ${err.message}"
  }
}
