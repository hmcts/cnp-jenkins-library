package uk.gov.hmcts.contino

class TeamNames {

  static final String DEFAULT_TEAM_NAME = 'pleaseTagMe'

  def teamNamesMap = ['bar':'Fees/Pay',
                      'bulk':'Software Engineering',
                      'ccd':'CCD',
                      'cmc':'Money Claims',
                      'div':'Divorce',
                      'dm':'Evidence Mment',
                      'em':'Evidence Mment',
                      'fees':'Fees/Pay',
                      'finrem':'Financial Remedy',
                      'ia':'Immigration',
                      'idam':'IdAM',
                      'payment':'Fees/Pay',
                      'rpe':'Software Engineering',
                      'sscs':'SSCS']

  def getName (String product) {
    return teamNamesMap.get(product, DEFAULT_TEAM_NAME)
  }
}
