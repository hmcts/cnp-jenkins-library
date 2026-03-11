import uk.gov.hmcts.contino.Helm

def call(Map params) {
  withAksClient(params.subscription, params.environment, params.product) {

    String chartName = "${params.product}-${params.component}"

    Helm helm = new Helm(this, chartName)

    def templateEnvVars = [
      "IMAGE_NAME=https://hmcts.azurecr.io/hmcts/${params.product}-${params.component}:latest",
    ]

    withEnv(templateEnvVars) {
      def values = []
      def helmResourcesDir = Helm.HELM_RESOURCES_DIR

      def templateValues = "${helmResourcesDir}/${chartName}/values.template.yaml"
      def defaultValues = "${helmResourcesDir}/${chartName}/values.yaml"
      if (fileExists(templateValues)) {
        sh "envsubst < ${templateValues} > ${defaultValues}"
      }
      values << defaultValues

      def valuesEnvTemplate = "${helmResourcesDir}/${chartName}/values.${params.environment}.template.yaml"
      def valuesEnv = "${helmResourcesDir}/${chartName}/values.${params.environment}.yaml"
      if (fileExists(valuesEnvTemplate)) {
        sh "envsubst < ${valuesEnvTemplate} > ${valuesEnv}"
        values << valuesEnv
      }

      helm.publishIfNotExists(values)
      helm.publishToGitIfNotExists(values)
    }
  }
}
