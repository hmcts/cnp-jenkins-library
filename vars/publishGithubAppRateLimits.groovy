import groovy.json.JsonOutput

def call(Map params = [:]) {
  def apps = params.apps ?: [
    'hmcts-jenkins-cnp',
    'hmcts-jenkins-cft',
    'hmcts-jenkins-rpe',
    'hmcts-jenkins-a-to-c',
    'hmcts-jenkins-d-to-i',
    'hmcts-jenkins-j-to-z'
  ]
  def container = params.container ?: 'github-app-rate-limits'
  def database = params.database ?: 'jenkins'
  def maxRetries = params.maxRetries ?: 3

  apps.each { credId ->
    def document
    try {
      withCredentials([usernamePassword(credentialsId: credId, usernameVariable: 'GH_APP_ID', passwordVariable: 'GH_APP_TOKEN')]) {
        // Single quotes so the token is expanded by the shell, not Groovy, and stays masked
        def raw = sh(returnStdout: true, script: '''
          curl -sSf \
            -H "Authorization: Bearer $GH_APP_TOKEN" \
            -H "Accept: application/vnd.github+json" \
            -H "X-GitHub-Api-Version: 2022-11-28" \
            https://api.github.com/rate_limit
        ''').trim()

        def rateLimit = readJSON text: raw
        document = JsonOutput.toJson([
          id              : UUID.randomUUID().toString(),
          credential_id   : credId,
          app_id          : env.GH_APP_ID,
          jenkins_instance: env.JENKINS_URL,
          job_name        : env.JOB_NAME,
          build_number    : env.BUILD_NUMBER,
          recorded_at     : new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone('UTC')),
          resources       : rateLimit.resources
        ])
      }
    } catch (Exception ex) {
      echo "ERROR: Failed to fetch rate limits for GitHub App ${credId}: ${ex}"
      return
    }

    def attempt = 0
    def success = false
    while (attempt < maxRetries && !success) {
      attempt++
      try {
        azureCosmosDBCreateDocument(container: container, credentialsId: 'cosmos-connection', database: database, document: document)
        success = true
      } catch (Exception ex) {
        echo "Attempt ${attempt}/${maxRetries} to publish rate limits for ${credId} failed: ${ex}"
        if (attempt < maxRetries) {
          sleep(time: (long) Math.pow(2, attempt), unit: 'SECONDS')
        }
      }
    }
    if (!success) {
      echo "ERROR: Gave up publishing rate limits for ${credId} after ${maxRetries} attempts"
    }
  }
}