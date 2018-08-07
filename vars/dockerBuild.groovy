import uk.gov.hmcts.contino.azure.Acr

def call(String imageName, Acr acr) {
    acr.login(env.REGISTRY_NAME)

    sh "docker build -t ${imageName} ."
    sh "docker push ${imageName}"
}
