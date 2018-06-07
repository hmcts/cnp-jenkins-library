#!groovy

/**
 * Run a closure inside a Docker container
 *
 * @param image The Docker image
 * @param options Extra 'docker run' arguments. e.g. bind-mounts, etc
 * @param body The closure
 */
def call(String image, String options, Closure body) {
  docker.image(image).inside(options) {
    body()
  }
}
