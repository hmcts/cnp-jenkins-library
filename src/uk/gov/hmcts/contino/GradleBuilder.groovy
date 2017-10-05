package uk.gov.hmcts.contino

class GradleBuilder implements Builder, Serializable {

  def steps
  def product

  GradleBuilder(steps, product) {
    this.steps = steps
    this.product = product
  }

  def build() {
    gradle("build")
    steps.stash(name: product, includes: "build/libs/*.jar")
  }

  def test() {
    gradle("test")
  }

  def smokeTest() {

  }

  def gradle(String task) {
    steps.sh("./gradlew ${task}")
  }
}
