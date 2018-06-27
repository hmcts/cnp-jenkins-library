package uk.gov.hmcts.contino

class RepositoryUrl {

  /**
   * Provides a github repository path
   * @param changeUrl the change url env variable from jenkins i.e. https://github.com/hmcts/spring-boot-template/pull/294
   * @return a repository path in the form of hmcts/spring-boot-template
   */
  String getFull(String changeUrl) {
    return changeUrl.replace("https://github.com/", "").replaceFirst("/pull/\\d+", "")
  }

  /**
   * Provides a Github repository name
   *
   * @param changeUrl the change url env variable from jenkins i.e. https://github.com/hmcts/spring-boot-template/pull/294
   * @return a repository name in the form of 'spring-boot-template'
   */
  String getShort(String changeUrl) {
    return changeUrl.tokenize('/.')[-3]
  }
}
