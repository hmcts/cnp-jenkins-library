package uk.gov.hmcts.tests

import spock.lang.Specification
import uk.gov.hmcts.contino.WebAppDeploy

class WebAppDeployTests  extends Specification {

  def 'should add the remote deployment endpoint' () {

    given:

      def steps = Mock(uk.gov.hmcts.tests.JenkinsStepMock)
      def deployer = new WebAppDeploy(steps, "product")

    when:

      deployer.deploy("env")

    then:

      1 * steps.git(['credentialsId': "WebAppDeployCredentials", 'url': "remote add azure \"https://product-env.scm.product-env.p.azurewebsites.net/product-env.git\""])

  }

}
