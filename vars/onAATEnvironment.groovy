import uk.gov.hmcts.contino.Environment

def call(String environment, Closure block) {
  if (new Environment(environment).isAATEnvironment()) {
    return block.call()
  }
}
