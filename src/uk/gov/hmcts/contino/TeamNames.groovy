package uk.gov.hmcts.contino

class TeamNames {

  static final String DEFAULT_TEAM_NAME = 'pleaseTagMe'

  def teamNamesMap = [

                      'ccd':'CCD',
                      'cmc':'Money Claims',
                      'custard':'CNP',
                      'div':'Divorce',
                      'dm':'Evidence Mment',
                      'em':'Evidence Mment',
                      'finrem':'Financial Remedy',
                      'ia':'Immigration',
                      'idam':'IdAM',
                      'fees':'Fees/Pay',
                      'fees-register':'Fees/Pay',
                      'payment':'Fees/Pay',
                      'ccpay':'Fees/Pay',
                      'bar':'Fees/Pay',
                      'probate':'Probate',
                      'bulk-scan':'Software Engineering',                  
                      'rpe':'Software Engineering',
                      'draft-store':'Software Engineering',
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
