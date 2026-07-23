def call(Map params = [:]) {
  def archiveJob = env.BUILD_ARCHIVE_JOB

  if (!archiveJob) {
    return
  }

  if (env.JOB_NAME == archiveJob) {
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
        string(name: 'SOURCE_BUILD_RESULT', value: currentBuild.result ?: 'SUCCESS'),
        string(name: 'SOURCE_PRODUCT', value: params.product ?: ''),
        string(name: 'SOURCE_COMPONENT', value: params.component ?: '')
      ]
    )
    echo "Queued build archive for ${env.JOB_NAME} #${env.BUILD_NUMBER}"
  } catch (err) {
    echo "Unable to queue build archive: ${err.message}"
  }
}
