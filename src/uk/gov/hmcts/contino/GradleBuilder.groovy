package uk.gov.hmcts.contino

class GradleBuilder implements Builder, Serializable {

  def steps

  GradleBuilder(steps) {
    this.steps = steps
  }

  def build() {
    gradle "build"
  }

  def test() {
    gradle "test"
  }

  def gradle(task) {
    steps.sh("./gradlew ${task}")
  }
}
