import uk.gov.hmcts.contino.Environment

def call(String environment, Closure block) {
  if (environment == new Environment(environment).nonProd) {
    return block.call()
  }
}
