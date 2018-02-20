package uk.gov.hmcts.contino;

public class AngularBuilder extends YarnBuilder {

  AngularBuilder(steps) {
    super(steps)
  }

  @Override
  def build() {
    super.build()

    yarn("build:ssr")
  }
}
