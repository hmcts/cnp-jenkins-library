import uk.gov.hmcts.contino.Environment

def call(String environment, Closure block) {

  if ((new Environment(env)).onFunctionalTestEnvironment(environment)) {
    return block.call()
  }
}
