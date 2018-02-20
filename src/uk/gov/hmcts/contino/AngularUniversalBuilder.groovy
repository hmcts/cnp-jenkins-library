package uk.gov.hmcts.contino


class AngularUniversalBuilder extends YarnBuilder {

  AngularUniversalBuilder(steps) {
    super(steps)
  }

  @Override
  def build() {
    super.build()

    yarn("build:ssr")
  }
}
