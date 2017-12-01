/**
 * retryAndSleep
 *
 * Runs the closure, retries if there's an error and then sleeps before trying again
 *
 * retryAndSleep(sleepDuration = 10, maxRetries = 10) {
 *   ...
 * }
 */
def call(Map<String, Integer> args, Closure body) {
  def config = [
    sleepDuration: 5,
    maxRetries: 5
  ] << args


  int sleepDuration = config.sleepDuration
  int maxRetries = config.maxRetries

  int retryCounter = 0

  retry(maxRetries) {
    try {
      steps.echo "Attempt number: " + (1 + retryCounter)
      body.call()
    } catch(err) {
      echo err.getClass().getName()

      +retryCounter
      if (retryCounter < maxRetries) {
        steps.sleep sleepDuration
      }
      throw err
    }
  }
}
