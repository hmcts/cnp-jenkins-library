package uk.gov.hmcts.contino.slack

import groovy.json.JsonSlurperClassic

class SlackChannelRetriever implements Serializable {

  def steps

  SlackChannelRetriever(steps) {
    this.steps = steps
  }

  /**
   * Maps a changeAuthor to a slack user, defaulting to a channel.
   *
   * @param channel a channel to use if change author is null
   * @param changeAuthor the change author.
   * @return a channel to notify
   */
  String retrieve(String channel, String changeAuthor) {
    if (changeAuthor == null || changeAuthor.empty) {
      return channel
    }
    def response = steps.httpRequest url: "https://raw.githubusercontent.com/hmcts/github-slack-user-mappings/master/slack.json", httpMode: 'GET', acceptType: 'APPLICATION_JSON'

    SlackUserMapping[] slackUserMapping = []
    if (response.content) {
      slackUserMapping = new JsonSlurperClassic()
        .parseText(response.content).users
    }

    SlackUserMapping mappedUser = slackUserMapping.find { user -> user.github == changeAuthor }
    return mappedUser != null? '@' + mappedUser.slack : null
  }
}
