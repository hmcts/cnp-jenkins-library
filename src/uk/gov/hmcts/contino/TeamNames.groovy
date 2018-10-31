package uk.gov.hmcts.contino

class TeamNames {

  static final String DEFAULT_TEAM_NAME = 'pleaseTagMe'

  def teamNamesMap = ['bar':'Fees/Pay',
                      'bulk-scan':'Software Engineering',
                      'ccd':'CCD',
                      'cmc':'Money Claims',
                      'custard':'CNP',
                      'div':'Divorce',
                      'dm':'CCD',
                      'draft-store':'Software Engineering',
                      'em':'Evidence Mment',
                      'fees':'Fees/Pay',
                      'finrem':'Financial Remedy',
                      'ia':'Immigration',
                      'idam':'IdAM',
                      'payment':'Fees/Pay',
                      'rpe':'Software Engineering',
                      'jui': 'Professional Applications',
                      'pui': 'Professional Applications',
                      'coh': 'Professional Applications',
                      'rpa': 'Professional Applications',
                      'rhubarb':'CNP',
                      'sscs':'SSCS']

  def getName (String product) {
    return teamNamesMap.get(product, DEFAULT_TEAM_NAME)
  }
}
