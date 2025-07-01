package uk.gov.hmcts.contino
import uk.gov.hmcts.contino.MetricsPublisher

class SlackAlerts {

  static void slack_message(Script script, String user, String colour, String message) {
      script.slackSend(
        channel: "${user}",
        color: "${colour}",
        message: "${message}"
      )
    }

  static boolean check_if_test_failed_then_report(localSteps) {
    def body =
      """-----------------------------
              The following test has failed twice today.
              Please fix within 3 days.
              -----------------------------
              * Job name: ${localSteps.config.env.JOB_NAME}
              * Build Number: ${localSteps.config.env.BUILD_NUMBER}
              * Build URL: ${localSteps.config.env.BUILD_URL}
              -----------------------------"""

    if (localSteps.currentBuild.result == 'FAILURE') {
      slack_message("${localSteps.config.slackUserID}", "warning", "${body}")
      return true
    } else {
      return false
    }
  }

    static boolean check_if_test_failed_intermitently_then_report(localSteps, max) {
      def loopMax = 20
      def LoopCount = 1
      def failureCount = 0
      def previousBuild = localSteps.currentBuild
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
              * Job name: ${localSteps.config.env.JOB_NAME}
              -----------------------------"""

      if (failureCount > 5) {
        slack_message("${localSteps.config.slackUserID}", "warning", "${body}")
        return true
      } else {
        return false
      }
    }
  }
