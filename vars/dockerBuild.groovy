import uk.gov.hmcts.contino.azure.Acr

def call(String imageName, subscription) {
    def acr = new Acr(this, subscription)
    acr.login(env.REGISTRY_NAME)

    //sh "docker login ${env.REGISTRY_HOST} -u ${env.REGISTRY_USERNAME} -p ${env.REGISTRY_PASSWORD}"
    sh "docker build -t ${imageName} ."
    sh "docker push ${imageName}"
}
