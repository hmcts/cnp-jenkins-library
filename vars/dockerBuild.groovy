def call(String imageName, String subscription) {

    // TODO replace with 'az acr login --name <acrName>' when switch to ACR and ditch the above,
    // TODO although not sure you can do this with SPs
    sh 'docker login $REGISTRY_HOST -u $REGISTRY_USERNAME -p $REGISTRY_PASSWORD'

    sh "docker build -t ${imageName} ."
    sh "docker push ${imageName}"
}
