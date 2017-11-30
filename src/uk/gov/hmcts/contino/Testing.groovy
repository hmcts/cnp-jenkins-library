package uk.gov.hmcts.contino
import org.apache.commons.lang3.RandomStringUtils

class Testing implements Serializable {

  private String PREPARE_ENVIRONMENT = 'export PATH=$PATH:/usr/local/bundle/bin:/usr/local/bin && export HOME="$WORKSPACE"'
  def pipe
  def branch

  Testing(pipe) {
    this.pipe = pipe
    this.branch = new ProjectBranch("${pipe.env.BRANCH_NAME}")
  }

  Testing(pipe, String gitUrl) {
    this.pipe = pipe
  }

  def unitTest() {
    return pipe.docker.image('dsanabria/terraform_validate:latest').inside {
      pipe.sh 'cd tests/unit && python tests.py'
    }
  }
  /* Running integration tests for a module with Chef Kitchen, spinning up temporary infrastructure
  * running tests and removing the infratructure at the end
  */
  def moduleIntegrationTests(String command="", envVars=[:]) {
    String RANDOM_STRING = RandomStringUtils.random(6, true, true)

    def envSuffix = branch.isMaster() ? 'dev' : "${branch.branchName}"

    return runWithDocker(command? command : "cd tests/int && kitchen test azure",
                         envVars? envVars : [TF_VAR_random_name:"tmp${RANDOM_STRING.toLowerCase()}",
                                             TF_VAR_branch_name:"${branch.branchName}"])
  }

  /* Running integration tests for a project only using Inspec on existing infrastructure */
  def projectRegressionTests() {
    return runWithDocker("inspec exec test/integration/default")
  }

  private runWithDocker(String command, envVars=[:]) {
    return pipe.docker
        .image("contino/azkitchentdi:latest")
        .inside(envVars.collect( { /-e $it.key=$it.value/ } ).join(" ")) {
      pipe.sh(PREPARE_ENVIRONMENT + " && " + command)
    }
  }

}
