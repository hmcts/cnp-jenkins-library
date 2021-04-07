package uk.gov.hmcts.pipeline

import uk.gov.hmcts.contino.ProjectBranch

class SonarProperties implements Serializable {
  static String get(steps) {
    def env = steps.env

    boolean onPR = new ProjectBranch(env.BRANCH_NAME).isPR()
    if (onPR) {
      return """-Dsonar.pullrequest.key=${env.CHANGE_ID} \
      -Dsonar.pullrequest.base=${env.CHANGE_TARGET} \
      -Dsonar.pullrequest.branch=${env.CHANGE_BRANCH} \
      -Dsonar.pullrequest.provider=github"""
    } else {
      return ""
    }
  }
}
