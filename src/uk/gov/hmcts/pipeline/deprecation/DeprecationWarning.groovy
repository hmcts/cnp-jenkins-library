package uk.gov.hmcts.pipeline.deprecation

import java.time.LocalDate

class DeprecationWarning implements Serializable {

  final String warningKey
  final String warningMessage
  final LocalDate deprecationDate

  DeprecationWarning(String warningKey, String warningMessage, LocalDate deprecationDate) {
    this.warningKey = warningKey
    this.warningMessage = warningMessage
    this.deprecationDate = deprecationDate
  }

}
