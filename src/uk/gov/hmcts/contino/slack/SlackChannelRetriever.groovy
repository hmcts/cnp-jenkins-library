package uk.gov.hmcts.contino.slack

import groovy.json.JsonSlurperClassic;

class SlackChannelRetriever {

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

    SlackMapping[] slackUserConfig = new JsonSlurperClassic()
      .parseText(steps.libraryResource('uk/gov/hmcts/contino/slack.json')).users

    SlackMapping mappedUser = slackUserConfig.find { user -> user.github == changeAuthor }
    if (mappedUser != null) {
      return '@' + mappedUser.slack
    } else {
      return '@'  + changeAuthor
    }
  }
}
