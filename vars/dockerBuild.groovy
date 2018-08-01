def call(String imageName) {
    sh "docker login ${env.REGISTRY_HOST} -u ${env.REGISTRY_USERNAME} -p ${env.REGISTRY_PASSWORD}"
    sh "docker build -t ${imageName} ."
    sh "docker push ${imageName}"
}
