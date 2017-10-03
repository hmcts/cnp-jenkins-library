package uk.gov.hmcts.contino

class GradleBuilder implements Builder, Serializable {

  def build() {
    gradle "build"
  }

  def test() {
    gradle "test"
  }

  def gradle(task) {
    sh "./gradlew ${task}"
  }
}
