import groovy.json.JsonBuilder
@Grab('com.squareup.okhttp3:okhttp:3.9.1')
@Grab('com.squareup.okio:okio:1.13.0')

import groovy.json.JsonSlurper
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import uk.gov.hmcts.contino.IPV4Validator
import uk.gov.hmcts.contino.ConsulRecord
/*--------------------------------------------------------------
Groovy script to update the scm service consul record. It will
crawl the infra to workout which webapps are currently deployed
and update the scm consul record with the right details. This
script is expected to be called as part of withPipeline just
after spinInfra.
 --------------------------------------------------------------*/

def call(environment) {
  call(environment, '')
}

def call(environment, deploymentTarget) {

  def environmentDt = "${environment}${deploymentTarget}"

  def envInfraRg = (deploymentTarget == '') ? "core-infra-$environment" : "env-infra-$environment"

  println "Registering application to the scm service"

  def az = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-jenkins az $cmd", returnStdout: true).trim() }
  def authtoken = az "account get-access-token --query accessToken -o tsv"

  // Get ServerFarms list
  println "Getting a list of the current apps deployed ..."
  Request requestfarms = new Request.Builder()
    .url("https://management.azure.com/subscriptions/${env.ARM_SUBSCRIPTION_ID}/resourceGroups/core-infra-$environmentDt/providers/Microsoft.Web/hostingEnvironments/core-compute-$environmentDt/sites?api-version=2016-09-01")
    .get()
    .addHeader("authorization", "Bearer " + authtoken)
    .addHeader("cache-control", "no-cache")
    .build()

  Response responsefarms = client.newCall(requestfarms).execute()

  def responsefarmsbody = new JsonSlurper().parseText(responsefarms.body().string())
  List siteslist = responsefarmsbody.value

// Get list of webapps using the ServerFarms list

  List webapps = []
  siteslist.each { site ->
    site.properties.each {
      k, v -> if (k == "defaultHostName") {
        webapps << v.split("\\.")[0]
      }
    }
  }

// Get ILB internal IP address
  println "Getting the ILB internal IP address ..."
  Request requestilbip = new Request.Builder()
    .url("https://management.azure.com/subscriptions/${env.ARM_SUBSCRIPTION_ID}/resourceGroups/core-infra-$environmentDt/providers/Microsoft.Web/hostingEnvironments/core-compute-$environmentDt/capacities/virtualip?api-version=2016-09-01")
    .get()
    .addHeader("authorization", "Bearer " + authtoken)
    .addHeader("cache-control", "no-cache")
    .build()

  Response responseilbip = client.newCall(requestilbip).execute()

  def responseilbipbody = new JsonSlurper().parseText(responseilbip.body().string())
  String ilbinternalip = responseilbipbody.internalIpAddress

  echo "ILB address is ${ilbinternalip}"
  boolean validIpAddress = IPV4Validator.validate(ilbinternalip)
  if (!validIpAddress) {
    error "IP address for the ILB of the ASE failed validation, exiting to prevent corruption of the scm records"
  }

// Get details about the consul vm scale set i.e. IP address
  println "Getting consul's IP address ..."
  Request requestvmss = new Request.Builder()
    .url("https://management.azure.com/subscriptions/${env.ARM_SUBSCRIPTION_ID}/resourceGroups/$envInfraRg/providers/Microsoft.Compute/virtualMachineScaleSets/consul-server/networkInterfaces?api-version=2017-03-30")
    .get()
    .addHeader("Authorization", "Bearer " + authtoken)
    .addHeader("Cache-Control", "no-cache")
    .build();

  Response responsevmss = client.newCall(requestvmss).execute();

  def consulvmscaleset = new JsonSlurper().parseText(responsevmss.body().string())
  String consulapiaddr = consulvmscaleset.value[0].properties.ipConfigurations[0].properties.privateIPAddress
  println "Consul IP address is: " + consulapiaddr

// Build json payload for scm record

  def scm = new ConsulRecord(ID: 'scm', Name: 'scm', Tags: webapps, Address: ilbinternalip, Port: 443 )
  def scmjson = new JsonBuilder(scm).toString()
  println("Preparing and sending scm consul record: " + scmjson)

// Update the scm record in consul

  MediaType mediaTypeScm = MediaType.parse("application/json");
  RequestBody scmrequestbody = RequestBody.create(mediaTypeScm, scmjson);
  Request requestscm = new Request.Builder()
    .url("http://" + consulapiaddr + ":8500/v1/agent/service/register")
    .put(scmrequestbody)
    .addHeader("content-type", "application/json")
    .addHeader("cache-control", "no-cache")
    .build();

  Response responsescm = client.newCall(requestscm).execute();

// Print the new record details

  println("Result code for scm service registration: " + responsescm.code())
}

