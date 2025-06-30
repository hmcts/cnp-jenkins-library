package uk.gov.hmcts.contino
import uk.gov.hmcts.contino.MetricsPublisher

class SlackAlerts {

  static void slack_message(String user, String colour, String message) {
      slackSend(
        channel: "${user}",
        color: "${colour}",
        message: "${message}"
      )
    }

  static Boolean check_if_test_failed_then_report(String user, config) {
    def body =
      """-----------------------------
              The following test has failed twice today.
              Please fix within 3 days.
              -----------------------------
              * Job name: ${config.env.JOB_NAME}
              * Build Number: ${config.env.BUILD_NUMBER}
              * Build URL: ${config.env.BUILD_URL}
              -----------------------------"""

    if (config.currentBuild.result == 'FAILURE') {
      slack_message("${user}", "warning", "${body}")
      return true
    } else {
      return false
    }
  }

    static Boolean check_if_test_failed_intermitently_then_report(String user, config) {
      def loopMax = 20
      def LoopCount = 1
      def failureCount = 0
      def previousBuild = config.currentBuild
      while (LoopCount < loopMax + 1) {
        if ((previousBuild.result == 'FAILURE')) {
          failureCount = failureCount + 1
        }
        previousBuild = previousBuild.previousBuild
        LoopCount = LoopCount + 1
      }

      def body =
        """-----------------------------
              The following test has failed ${failureCount} times in 20 executions.
              Please fix within 3 days.
              -----------------------------
              * Job name: ${config.env.JOB_NAME}
              -----------------------------"""

      if (failureCount > 5) {
        slack_message("${user}", "warning", "${body}")
        return true
      } else {
        return false
      }
    }
  }
