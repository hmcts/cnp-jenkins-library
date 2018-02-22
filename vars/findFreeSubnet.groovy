#!groovy
//@Grab(group='commons-net', module='commons-net', version='3.6')
@Grab(group='com.github.seancfoley', module='ipaddress', version='4.1.0')

import inet.ipaddr.*
import inet.ipaddr.ipv4.*

import groovy.json.JsonSlurperClassic

def call(subscription) {
  result = sh(script: "az network vnet list --query '[].[name,addressSpace.addressPrefixes]' -o json", returnStdout: true).trim()
  vnetList = new JsonSlurperClassic().parseText(result)
  echo "Existing subnetworks in current environment: ${vnetList.join("\n")}"

  ipList = vnetList.collect { it[1][0] }

  list = getSubnetsList(env.TF_VAR_root_address_space, 6)
  subnetsList = list.collect { it.toString() }
  echo "All possible subnets list: $subnetsList"

  def chosenIP
  switch (subscription) {
    case 'prod':
      chosenIP = (subnetsList[0..2] - ipList)[0]
      break
    case 'nonprod':
      chosenIP = (subnetsList[3..14] - ipList)[0]
      break
    case 'sandbox':
      chosenIP = (subnetsList[15..-1] - ipList)[0]
      break
  }

  if (chosenIP != [])
    return chosenIP
  else
    throw new Exception("Could not find unused subnetwork!")
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
