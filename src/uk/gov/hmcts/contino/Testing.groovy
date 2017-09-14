package uk.gov.hmcts.contino
import org.apache.commons.lang3.RandomStringUtils

class Testing implements Serializable {

  private String PREPARE_ENVIRONMENT = 'export PATH=$PATH:/usr/local/bundle/bin:/usr/local/bin && export HOME="$WORKSPACE"'
  def pipe
  def String gitUrl

  Testing(pipe){
    this.pipe = pipe
    this.gitUrl = "${pipe.GITHUB_PROTOCOL}://${pipe.TOKEN}@${pipe.GITHUB_REPO}"
  }

  Testing(pipe, String gitUrl){
    this.pipe = pipe
    this.gitUrl = gitUrl
  }

  def unitTest() {
    return pipe.docker.image('dsanabria/terraform_validate:latest').inside {
      pipe.sh 'cd tests/unit && python tests.py'
    }
  }
  /* Running integration tests for a module with Chef Kitchen, spinning up temporary infrastructure
  * running tests and removing the infratructure at the end
  */
  def moduleIntegrationTests() {
    String RANDOM_STRING = RandomStringUtils.random(6, true, true)
    def envSuffix = (pipe.env.BRANCH_NAME == 'master') ? 'dev' : pipe.env.BRANCH_NAME

    return runWithDocker("cd tests/int && kitchen test azure",
                         [TF_VAR_random_name:"inspec-${envSuffix}-${RANDOM_STRING}",
                          TF_VAR_branch_name:pipe.env.BRANCH_NAME])
  }

  /* Running integration tests for a project only using Inspec on existing infrastructure */
  def projectRegressionTests() {
    return runWithDocker("inspec exec test/integration/default")
  }

  private runWithDocker(String command, envVars=[:]) {
    return pipe.docker
        .image("contino/inspec-azure:latest")
        .inside(envVars.collect( { /-e $it.key=$it.value/ } ).join(" ")) {
      pipe.sh(PREPARE_ENVIRONMENT + " && " + command)
    }
  }

}
