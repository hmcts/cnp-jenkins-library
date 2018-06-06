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
    slice 13 - hmcts demo environment
    anything else automatically selected from free slices 3-12
  subnet 15-end for sandbox subscription
*/
def call(String subscription, String environment) {
  def az = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$subscription az $cmd", returnStdout: true).trim() }

  result = az "network vnet list --query '[].[name,addressSpace.addressPrefixes]' -o json"
  vnetList = new JsonSlurperClassic().parseText(result)
  log.info("Existing subnetworks in current environment: ${vnetList.join("\n")}")

  ipList = vnetList.collect { it[1][0] }

  list = getSubnetsList(env.TF_VAR_root_address_space, 6)
  subnetsList = list.collect { it.toString() }

  def chosenIP
  switch (subscription) {
    case 'prod':
      chosenIP = subnetsList[1]
      break
    case 'nonprod':
      if (environment.equalsIgnoreCase("aat"))
        chosenIP = subnetsList[2]
      else
        chosenIP = (subnetsList[3..12] - ipList)[0]
      echo "List of all possible subnets for $subscription: ${subnetsList[2..12]}"
      break
    case 'hmctsdemo':
      chosenIP = subnetsList[13]
      echo "Subnet for $subscription: ${subnetsList[13]}"
      break
    case 'sandbox':
      chosenIP = (subnetsList[14..-1] - ipList)[0]
      echo "List of all possible subnets for $subscription: ${subnetsList[14..-1]}"
      break
  }

  if (ipList.contains(chosenIP))
    log.warning("${subnetsList[1]} already in use!")

  if (chosenIP != null)
    return [chosenIP, subnetsList.findIndexValues { it.equalsIgnoreCase(chosenIP) }[0] ]
  else
    throw new Exception("Could not find a free subnetwork!")
}

def getSubnetsList(String rootSubnet, newbits) {
  list=[]

  def getNext
  getNext = { addr, rootNet, list ->
      list.add(addr)
      hostMask = list[-1].getNetwork().getHostMask(list[-1].getNetworkPrefixLength())
      broadcastAddress = list[-1].bitwiseOr(hostMask)
      /*println("Subnet: ${list[-1]}\n" +
        "Network Mask: ${list[-1].getNetwork().getNetworkMask(list[-1].getPrefixLength(),false)}\n" +
        "Wildcard: $hostMask\n" +
        "Broadcast: $broadcastAddress\n" +
        "-------------")*/

      nextAddress = new IPv4Address(broadcastAddress.getValue()+1, list[-1].getPrefixLength())
      if (rootNet.contains(nextAddress)) {
        /*println("$rootNet contains $nextAddress")*/
        getNext(nextAddress.setPrefixLength(list[-1].getPrefixLength()), rootNet, list)
      }
      else
        return list
    }

  rSubnet = new IPAddressString(rootSubnet).getAddress()
  /*println("$rootSubnet can be split in ${Math.pow(2,newbits)} subnets of class /${rSubnet.getPrefixLength()+newbits}")*/
  return getNext(rSubnet.setPrefixLength(rSubnet.getPrefixLength()+newbits), rSubnet, list)
}
