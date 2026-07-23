def call() {
  try {
    archiveArtifacts(
      allowEmptyArchive: true,
      artifacts: [
        '**/build/reports/**',
        '**/build/test-results/**',
        '**/playwright-report/**',
        '**/test-results/**',
        'api-output/**',
        'e2e-output/**',
        'functional-output/**',
        'smoke-output/**',
        'pods-logs-*/**'
      ].join(',')
    )
  } catch (err) {
    echo "Unable to archive build outputs: ${err.message}"
  }
}
