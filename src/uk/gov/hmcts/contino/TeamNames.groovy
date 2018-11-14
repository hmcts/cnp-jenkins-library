package uk.gov.hmcts.contino

class TeamNames {

  static final String DEFAULT_TEAM_NAME = 'pleaseTagMe'

  def teamNamesMap = [

                      'ccd':'CCD',
                      'cmc':'Money Claims',
                      'custard':'CNP',
                      'cet': 'Civil Enforcement',
                      'div':'Divorce',
                      'dm':'CCD',
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
                      'ref': 'Professional Applications',
                      'rhubarb':'CNP',
                      'sscs':'SSCS',
                      'sscs-cor':'SSCS',
                      'sscs-tya':'SSCS',
                      'sscs-tribunals':'SSCS',
                      'snl':'SnL'
  ]

  def getName (String product) {
    if (product.startsWith('pr-')) {
      product = getRawProductName(product)
    }
    return teamNamesMap.get(product, DEFAULT_TEAM_NAME)
  }
  
  def getRawProductName (String product) {
    return product.split('pr-(\\d+)-')[1];
  }
}
