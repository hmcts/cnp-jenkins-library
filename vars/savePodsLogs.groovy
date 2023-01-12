import uk.gov.hmcts.contino.DockerImage


def call(DockerImage dockerImage, Map params, String scope) {
  onPR {
    stageWithAgent("Save pods logs", params.product) {
      def chartName = "${params.product}-${params.component}"
      def imageTag = dockerImage.getTag()
      def releaseName = "${chartName}-${imageTag}"

      steps.writeFile(file: 'save-pods-logs.sh', text: steps.libraryResource('uk/gov/hmcts/helm/save-pods-logs.sh'))
      steps.sh """
        chmod +x save-pods-logs.sh
        ./save-pods-logs.sh ${releaseName} ${env.TEAM_NAMESPACE} pods-logs-${scope}
        rm -f save-pods-logs.sh
      """
      steps.archiveArtifacts allowEmptyArchive: true, artifacts: "pods-logs-${scope}/**"
    }
  }
}
