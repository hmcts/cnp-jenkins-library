package uk.gov.hmcts.contino

class TeamNames {

  def teamNamesMap = ['bar':'Fees/Pay',
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
                      'sscs':'SSCS']

  def getName (String product) {
    return teamNamesMap.get(product, 'pleaseTagMe')
  }
}
