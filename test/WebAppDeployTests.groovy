package uk.gov.hmcts.tests

import spock.lang.Ignore
import spock.lang.Specification
import uk.gov.hmcts.contino.WebAppDeploy

@Ignore("TBD too much time figting the test framework")
class WebAppDeployTests  extends Specification {


  def 'should add the remote deployment endpoint' () {

    given:

      def steps = Mock(uk.gov.hmcts.tests.JenkinsStepMock)
      def deployer = new WebAppDeploy(steps, "product")
    when:

      deployer.deploy("env")

    then:

      1 * steps.sh("git remote add azure \"https://\$GIT_DEPLOY_USERNAME:\$GIT_DEPLOY_PASSWORD@product-env.scm.product-env.p.azurewebsites.net/product-env.git\"")

  }

  def 'should push to the deployment remote' () {

    given:

      def steps = Mock(uk.gov.hmcts.tests.JenkinsStepMock)
      def deployer = new WebAppDeploy(steps, "product")

    when:

      deployer.deploy("env")

    then:
      1 * steps.sh("git push azure master")
  }

}
