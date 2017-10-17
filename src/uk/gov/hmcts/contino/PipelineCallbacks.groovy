package uk.gov.hmcts.contino

class PipelineCallbacks implements Serializable {

  Map<String, Closure> bodies = new HashMap<>()

  void afterCheckout(Closure body) {
    after('checkout', body)
  }

  void before(String stage, Closure body) {
    bodies.put("before:${stage}", body)
  }

  void after(String stage, Closure body) {
    bodies.put("after:${stage}", body)
  }

  void callAfter(String stage) {
    nullSafeCall("after:${stage}")
  }

  void callBefore(String stage) {
    nullSafeCall("before:${stage}")
  }

  void callAround(String stage, Closure body) {
    callBefore(stage)
    body.call()
    callAfter(stage)
  }

  private def nullSafeCall(String key) {
    def body = bodies.get(key)
    if (body != null) {
      body.call()
    }
  }
}
