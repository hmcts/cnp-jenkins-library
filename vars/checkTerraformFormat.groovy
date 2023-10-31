// import uk.gov.hmcts.contino.DockerImage
// import uk.gov.hmcts.contino.Kubectl


// def call(DockerImage dockerImage, Map params, String scope) {
//   try {
//     def chartName = "${params.product}-${params.component}"
//     def imageTag = dockerImage.getImageTag()
//     def releaseName = "${chartName}-${imageTag}"
//     def namespace = env.TEAM_NAMESPACE

def call ()
    withAksClient(params.subscription, params.environment, params.product) {
      def kubectl = new Kubectl(this, params.subscription, namespace, params.aksSubscription.name)
      kubectl.login()

      steps.writeFile(file: 'check-terraform-format.sh', text: steps.libraryResource('uk/gov/hmcts/helm/check-terraform-format'))
      steps.sh(label: "Save pods logs in artifacts, under pods-logs-${scope}", script: """
        chmod +x check-terraform-format.sh
        ./check-terraform-format.sh ${releaseName} ${namespace} pods-logs-${scope}
        rm -f check-terraform-format.sh
      """)

      steps.archiveArtifacts(allowEmptyArchive: true, artifacts: "pods-logs-${scope}/**")
    }
  }
}
