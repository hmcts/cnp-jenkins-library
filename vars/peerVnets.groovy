import uk.gov.hmcts.contino.*

def call(String mgmtName, String mgmtSubscriptionID, String peeredVnetSuffix, String peeredVnetSubscriptionID) {

  stage("Peer Vnet to Management") {
    if (!mgmtSubscriptionID == peeredVnetSubscriptionID){
      log.error("Peering between networks in different subscriptions is not yet supported!")
      throw new Exception("vnet peering in different subscriptions not yet supported!")
    }

    log.info "Peering $mgmtName vnet in $mgmtName resource group to core-infra-vnet-$peeredVnetSuffix vnet in core-infra-$peeredVnetSuffix"
    def functions = libraryResource 'uk/gov/hmcts/contino/peerVnets.sh'
    writeFile file: 'peerVnets.sh', text: functions

    result = sh "bash peerVnets.sh ${mgmtName} ${mgmtSubscriptionID} ${peeredVnetSuffix} ${peeredVnetSubscriptionID}"
  }
}

