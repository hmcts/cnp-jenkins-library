#!groovy
//@Grab(group='commons-net', module='commons-net', version='3.6')
@Grab(group='com.github.seancfoley', module='ipaddress', version='4.1.0')

import inet.ipaddr.*
import inet.ipaddr.ipv4.*

import groovy.json.JsonSlurperClassic

/*
  subnet 0-14 for prod & nonprod subscriptions
    slice 0 - management vnet
    slice 1 - prod environment
    slice 2 - nonprod environment
    anything else automatically selected from free slices 3-14
  subnet 15-end for sandbox subscription
*/
def call(String subscription, String environment, Integer numberOfSubnets) {
  def az = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$subscription az $cmd", returnStdout: true).trim() }

  result = az "network vnet list --query '[].[name,addressSpace.addressPrefixes]' -o json"
  vnetList = new JsonSlurperClassic().parseText(result)
  log.info("Existing subnetworks in current environment: ${vnetList.join("\n")}")

  ipList = vnetList.collect { it[1][0] }

  list = getSubnetsList(env.TF_VAR_root_address_space, 6)
  subnetsList = list.collect { it.toString() }

  def chosenIP = []
  switch (subscription) {
    case 'nonprod':
      if (environment.equalsIgnoreCase("aat")) {
        chosenIP.add(subnetsList[2])
        if (numberOfSubnets>1)
          chosenIP.add((subnetsList[3..14] - ipList)[0..numberOfSubnets-1])
        log.warning("Can't return ${numberOfSubnets} subnets for ${environment}! Returning one only!")
      }
      else
        chosenIP = (subnetsList[3..14] - ipList)[0..numberOfSubnets-1]
      echo "List of all possible subnets for $subscription: ${subnetsList[2..14]}. Choosing: ${chosenIP}"
      break
    case 'sandbox':
      chosenIP = (subnetsList[15..-1] - ipList)[0..1]
      echo "List of all possible subnets for $subscription: ${subnetsList[15..-1]}. Choosing: ${chosenIP}"
      break
  }

  return chosenIP.collect { item -> [item, subnetsList.findIndexOf { it.equalsIgnoreCase(item) }] }
}

def getSubnetsList(String rootSubnet, newbits) {
  list=[]

  def getNext
  getNext = { addr, rootNet, list ->
      list.add(addr)
      hostMask = list[-1].getNetwork().getHostMask(list[-1].getNetworkPrefixLength())
      broadcastAddress = list[-1].bitwiseOr(hostMask)

      nextAddress = new IPv4Address(broadcastAddress.getValue()+1, list[-1].getPrefixLength())
      if (rootNet.contains(nextAddress)) {
        getNext(nextAddress.setPrefixLength(list[-1].getPrefixLength()), rootNet, list)
      }
      else
        return list
    }

  rSubnet = new IPAddressString(rootSubnet).getAddress()
  return getNext(rSubnet.setPrefixLength(rSubnet.getPrefixLength()+newbits), rSubnet, list)
}
