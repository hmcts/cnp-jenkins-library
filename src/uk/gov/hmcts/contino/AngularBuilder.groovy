package uk.gov.hmcts.contino

class AngularBuilder extends YarnBuilder {

  AngularBuilder(steps) {
    super(steps)
  }

  @Override
  def build() {
    println "!!! inside build"
    super.build()

    yarn("build:ssr")
  }
}
