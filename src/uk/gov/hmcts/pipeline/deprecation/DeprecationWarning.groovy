package uk.gov.hmcts.pipeline.deprecation

import java.time.LocalDate

class DeprecationWarning implements Serializable {

  final String warningKey
  final String warningMessage
  final LocalDate deprecationDate
  final boolean includeDeadlineMessage

  DeprecationWarning(String warningKey, String warningMessage, LocalDate deprecationDate, boolean includeDeadlineMessage = true) {
    this.warningKey = warningKey
    this.warningMessage = warningMessage
    this.deprecationDate = deprecationDate
    this.includeDeadlineMessage = includeDeadlineMessage
  }

}
