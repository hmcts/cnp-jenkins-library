import uk.gov.hmcts.contino.Consul
import uk.gov.hmcts.contino.Helm
import uk.gov.hmcts.contino.Kubectl

def call(Map params) {
  withAksClient(params.subscriptionName, params.environmentName) {

    String chartName = "${params.product}-${params.component}"

    Helm helm = new Helm(this, chartName)
    helm.init()

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

      def valuesEnvTemplate = "${helmResourcesDir}/${chartName}/values.${params.environmentName}.template.yaml"
      def valuesEnv = "${helmResourcesDir}/${chartName}/values.${params.environmentName}.yaml"
      if (fileExists(valuesEnvTemplate)) {
        sh "envsubst < ${valuesEnvTemplate} > ${valuesEnv}"
        values << valuesEnv
      }

      helm.publishIfNotExists(values)
    }
  }
}
