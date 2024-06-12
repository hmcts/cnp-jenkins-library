import uk.gov.hmcts.contino.slack.SlackChannelRetriever
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.pipeline.deprecation.WarningCollector
import uk.gov.hmcts.contino.MetricsPublisher
import groovy.json.JsonOutput

/**
 * Send build failure notification
 * <p>
 * When build happens on master branch then specified team channel is notified. In other cases change author is notified instead.
 * <p>
 * When change author does not have Slack account or uses other Slack username than one registered in LDAP then notification won't be sent.
 *
 * @param args arguments:
 *  <ul>
 *      <li>channel - (string; required) name of the slack channel for team notifications</li>
 *  </ul>
 */
def call(String teamSlackChannel, MetricsPublisher metricsPublisher ) {
  String slackWarningMessage = ""
  String warningMessage = WarningCollector.getSlackWarningMessage()
  String changeAuthor = env.CHANGE_AUTHOR

  publishWarningMetrics(metricsPublisher)

  if(!warningMessage.trim().isEmpty()){

    echo warningMessage
    String channel
    if(channel == null ) {
      channel = teamSlackChannel
    }

    def blocks = [
        [
            type: "header",
            text: [
                type: "plain_text",
                text: "Deprecated Config in ${env.JOB_NAME}",
                emoji: true
            ]
        ],
        [
            type: "section",
            fields: [
                [
                    type: "mrkdwn",
                    text: "*Source:*\nBuild ${env.BUILD_DISPLAY_NAME}"
                ],
                [
                    type: "mrkdwn",
                    text: "*Build:*\n<${env.RUN_DISPLAY_URL}>"
                ]
            ]
        ]
    ]

    if (warningMessage) {
        blocks.add([
            type: "divider"
        ])
        blocks.add([
            type: "section",
            fields: [
                [
                    type: "mrkdwn",
                    text: "*Warning:*\n${warningMessage}"
                ]
            ]
        ])
    }

    try {
      slackSend(
        failOnError: true,
        channel: channel,
        color: 'warning',
        message: blocks)
    } 
    catch (Exception ex) {
      if(channel!='@iamabotuser') {
        throw new Exception("ERROR: Failed to notify ${channel} due to the following error: ${ex}")
      }
    }
  }
}

void publishWarningMetrics(MetricsPublisher metricsPublisher) {
  for (pipelineWarning in WarningCollector.pipelineWarnings) {
    metricsPublisher.publish(pipelineWarning.warningKey)
  }

}
