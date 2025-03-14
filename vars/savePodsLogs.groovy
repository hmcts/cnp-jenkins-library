import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.Kubectl


def call(DockerImage dockerImage, Map params, String scope) {
  try {
    def chartName = "${params.product}-${params.component}"
    def imageTag = dockerImage.getImageTag()
    def releaseName = "${chartName}-${imageTag}"
    def namespace = env.TEAM_NAMESPACE

    withAksClient(params.subscription, params.environment, params.product) {
      def kubectl = new Kubectl(this, params.subscription, namespace, params.aksSubscription.name)
      kubectl.login()

      writeFile(file: 'save-pods-logs.sh', text: steps.libraryResource('uk/gov/hmcts/helm/save-pods-logs.sh'))
      sh(label: "Save pods logs in artifacts, under pods-logs-${scope}", script: """
        chmod +x save-pods-logs.sh
        ./save-pods-logs.sh ${releaseName} ${namespace} pods-logs-${scope}
        rm -f save-pods-logs.sh
      """)

      archiveArtifacts(allowEmptyArchive: true, artifacts: "pods-logs-${scope}/**")
    }
  } catch(err) {
    echo "Unable to complete saving pods logs in Jenkins artifacts"
  }
}
