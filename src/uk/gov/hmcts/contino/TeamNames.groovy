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
                      'dg':'Evidence Mment',
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
                      'pbi':'Software Engineering',
                      'jui': 'Professional Applications',
                      'pui': 'Professional Applications',
                      'xui': 'Professional Applications',
                      'coh': 'Professional Applications',
                      'rpa': 'Professional Applications',
                      'rpx': 'Professional Applications',
                      'ref': 'Professional Applications',
                      'rhubarb':'CNP',
                      'rhubarb-shared-infrastructure' : 'CNP',
                      'plum':'CNP',
                      'crumble':'CNP',
                      'plum-shared-infrastructure' : 'CNP',
                      'sscs':'SSCS',
                      'sscs-cor':'SSCS',
                      'sscs-tya':'SSCS',
                      'sscs-tribunals':'SSCS',
                      'snl':'SnL',
                      'am':'AM',
                      'fpl': 'Family Public Law',
                      'ctsc': 'CTSC',
                      'rd': 'Reference Data',
                      'data-extractor' : 'Software Engineering'
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

  def getNameNormalizedOrThrow(String product) {
    if (product.startsWith('pr-')) {
      product = getRawProductName(product)
    }
    if (!teamNamesMap.containsKey(product)) {
      throw new RuntimeException(
        "Product ${product} does not belong to any team. "
        + "Please create a PR to update TeamNames in the Jenkins library."
      )
    }
    return teamNamesMap.get(product)
      .toLowerCase()
      .replace("/", "-")
      .replace(" ", "-")
  }

}
