package uk.gov.hmcts.contino

class MockDocker {

  def inside(Object options, Closure body) {
    body.call()
  }

  def image(String image) {
    return this
  }
}
