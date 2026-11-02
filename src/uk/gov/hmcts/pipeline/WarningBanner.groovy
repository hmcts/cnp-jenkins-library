package uk.gov.hmcts.pipeline

class WarningBanner {

  static final String RESOURCE_PATH = 'uk/gov/hmcts/pipeline/warning-banner.txt'

  static String get(steps) {
    return steps.libraryResource(RESOURCE_PATH)
  }
}