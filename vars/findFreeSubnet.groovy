#!groovy
//@Grab(group='commons-net', module='commons-net', version='3.6')
@Grab(group='com.github.seancfoley', module='ipaddress', version='4.1.0')

import inet.ipaddr.*
import inet.ipaddr.ipv4.*

import groovy.json.JsonSlurperClassic

def call(subscription) {

  //result = sh(script: "az network vnet list --query '[].[name,addressSpace.addressPrefixes,subnets[*].addressPrefix,dhcpOptions.dnsServers[*]]' -o json", returnStdout: true).trim()
  result = sh(script: "az network vnet list --query '[].[name,addressSpace.addressPrefixes]' -o json", returnStdout: true).trim()
  vnetList = new JsonSlurperClassic().parseText(result)
  echo "Existing subnetworks: ${vnetList.each { vnet -> println(vnet) }}"

  ipList=[]
  vnetList.each { vnet -> ipList.add(vnet[1][0]) }

  list = getSubnetsList(env.TF_VAR_root_address_space, 6)
  subnetsList = []
  list.each { it -> subnetsList.add(it.toString()) }

  if (forSubscription.equalsIgnoreCase("sandbox"))
    chosenIP = (subnetsList[15..-1]-ipList)[0]
  else
    chosenIP = (subnetsList[3..14]-ipList)[0]

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
      println("Subnet: ${list[-1]}")
      println("Network Mask: ${list[-1].getNetwork().getNetworkMask(list[-1].getPrefixLength(),false)}")
      println("Wildcard: $hostMask")
      println("Broadcast: $broadcastAddress")
      println("-------------")

      nextAddress = new IPv4Address(broadcastAddress.getValue()+1, list[-1].getPrefixLength())
      if (rootNet.contains(nextAddress)) {
        println("$rootNet contains $nextAddress")
        getNext(nextAddress.setPrefixLength(list[-1].getPrefixLength()), rootNet, list)
      }
      else
        return list
    }

  rSubnet = new IPAddressString(rootSubnet).getAddress()
  println("$rootSubnet can be split in ${Math.pow(2,newbits)} subnets of class /${rSubnet.getPrefixLength()+newbits}")
  return getNext(rSubnet.setPrefixLength(rSubnet.getPrefixLength()+newbits), rSubnet, list)
}


/*
groovy:000> def getNext(String rootSubnet, newbits) {
groovy:001>   list=[]
groovy:002>   def getNext
groovy:003>   getNext = { addr, rootNet, list ->
groovy:004>       list.add(addr)
groovy:005>       hostMask = list[-1].getNetwork().getHostMask(list[-1].getNetworkPrefixLength())
groovy:006>       broadcastAddress = list[-1].bitwiseOr(hostMask)
groovy:007>       println("Subnet: ${list[-1]}")
groovy:008>       println("Network Mask: ${list[-1].getNetwork().getNetworkMask(list[-1].getPrefixLength(),false)}")
groovy:009>       println("Wildcard: $hostMask")
groovy:010>       println("Broadcast: $broadcastAddress")
groovy:011>       println("-------------")
groovy:012>
groovy:012>       nextAddress = new IPv4Address(broadcastAddress.getValue()+1, list[-1].getPrefixLength())
groovy:013>       if (rootNet.contains(nextAddress)) {
groovy:014>         println("$rootNet contains $nextAddress")
groovy:015>         getNext(nextAddress.setPrefixLength(list[-1].getPrefixLength()), rootNet, list)
groovy:016>       }
groovy:017>       else
groovy:018>         return list
groovy:019>     }
groovy:020>
groovy:020>   rSubnet = new IPAddressString(rootSubnet).getAddress()
groovy:021>   return getNext(rSubnet.setPrefixLength(rSubnet.getPrefixLength()+newbits), rSubnet, list)
groovy:022> }
===> true
groovy:000> getNext("10.96.0.0/12", 3)
Subnet: 10.96.0.0/15
Network Mask: 255.254.0.0
Wildcard: 0.1.255.255
Broadcast: 10.97.255.255
-------------
10.96.0.0/12 contains 10.98.0.0/15
Subnet: 10.98.0.0/15
Network Mask: 255.254.0.0
Wildcard: 0.1.255.255
Broadcast: 10.99.255.255
-------------
10.96.0.0/12 contains 10.100.0.0/15
Subnet: 10.100.0.0/15
Network Mask: 255.254.0.0
Wildcard: 0.1.255.255
Broadcast: 10.101.255.255
-------------
10.96.0.0/12 contains 10.102.0.0/15
Subnet: 10.102.0.0/15
Network Mask: 255.254.0.0
Wildcard: 0.1.255.255
Broadcast: 10.103.255.255
-------------
10.96.0.0/12 contains 10.104.0.0/15
Subnet: 10.104.0.0/15
Network Mask: 255.254.0.0
Wildcard: 0.1.255.255
Broadcast: 10.105.255.255
-------------
10.96.0.0/12 contains 10.106.0.0/15
Subnet: 10.106.0.0/15
Network Mask: 255.254.0.0
Wildcard: 0.1.255.255
Broadcast: 10.107.255.255
-------------
10.96.0.0/12 contains 10.108.0.0/15
Subnet: 10.108.0.0/15
Network Mask: 255.254.0.0
Wildcard: 0.1.255.255
Broadcast: 10.109.255.255
-------------
10.96.0.0/12 contains 10.110.0.0/15
Subnet: 10.110.0.0/15
Network Mask: 255.254.0.0
Wildcard: 0.1.255.255
Broadcast: 10.111.255.255
-------------
===> [10.96.0.0/15, 10.98.0.0/15, 10.100.0.0/15, 10.102.0.0/15, 10.104.0.0/15, 10.106.0.0/15, 10.108.0.0/15, 10.110.0.0/15]
groovy:000>

// a previous version
groovy:000>
groovy:000> def getNext(addr, root, list=[]) {
groovy:001>   list.add(new IPAddressString(addr).getAddress())
groovy:002>   hostMask = list[-1].getNetwork().getHostMask(list[-1].getNetworkPrefixLength())
groovy:003>   broadcastAddress = list[-1].bitwiseOr(hostMask)
groovy:004>   println("Subnet: ${list[-1]}")
groovy:005>   println("Network Mask: ${list[-1].getNetwork().getNetworkMask(list[-1].getPrefixLength(),false)}")
groovy:006>   println("Wildcard: $hostMask")
groovy:007>   println("Broadcast: $broadcastAddress")
groovy:008>   println("-------------")
groovy:009>
groovy:009>   nextAddress = new IPv4Address(broadcastAddress.getValue()+1, list[-1].getPrefixLength())
groovy:010>   rootNet = new IPAddressString(root).getAddress()
groovy:011>   if (rootNet.contains(nextAddress)) {
groovy:012>     println("$rootNet contains $nextAddress")
groovy:013>     getNext(nextAddress.setPrefixLength(list[-1].getPrefixLength()).toString(), root, list)
groovy:014>   }
groovy:015>   else
groovy:016>     return list
groovy:017> }
===> true
groovy:000> getNext("10.96.0.0/15", "10.96.0.0/12")
Subnet: 10.96.0.0/15
Network Mask: 255.254.0.0
Wildcard: 0.1.255.255
Broadcast: 10.97.255.255
-------------
10.96.0.0/12 contains 10.98.0.0/15
Subnet: 10.98.0.0/15
Network Mask: 255.254.0.0
Wildcard: 0.1.255.255
Broadcast: 10.99.255.255
-------------
10.96.0.0/12 contains 10.100.0.0/15
Subnet: 10.100.0.0/15
Network Mask: 255.254.0.0
Wildcard: 0.1.255.255
Broadcast: 10.101.255.255
-------------
10.96.0.0/12 contains 10.102.0.0/15
Subnet: 10.102.0.0/15
Network Mask: 255.254.0.0
Wildcard: 0.1.255.255
Broadcast: 10.103.255.255
-------------
10.96.0.0/12 contains 10.104.0.0/15
Subnet: 10.104.0.0/15
Network Mask: 255.254.0.0
Wildcard: 0.1.255.255
Broadcast: 10.105.255.255
-------------
10.96.0.0/12 contains 10.106.0.0/15
Subnet: 10.106.0.0/15
Network Mask: 255.254.0.0
Wildcard: 0.1.255.255
Broadcast: 10.107.255.255
-------------
10.96.0.0/12 contains 10.108.0.0/15
Subnet: 10.108.0.0/15
Network Mask: 255.254.0.0
Wildcard: 0.1.255.255
Broadcast: 10.109.255.255
-------------
10.96.0.0/12 contains 10.110.0.0/15
Subnet: 10.110.0.0/15
Network Mask: 255.254.0.0
Wildcard: 0.1.255.255
Broadcast: 10.111.255.255
-------------
===> [10.96.0.0/15, 10.98.0.0/15, 10.100.0.0/15, 10.102.0.0/15, 10.104.0.0/15, 10.106.0.0/15, 10.108.0.0/15, 10.110.0.0/15]
groovy:000>


 */
