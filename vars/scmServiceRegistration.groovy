@Grab('com.squareup.okhttp3:okhttp:3.9.1')
@Grab('com.squareup.okio:okio:1.13.0')

import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType
import okhttp3.RequestBody

/*--------------------------------------------------------------
Groovy script to update the scm service consul record. It will
crawl the infra to workout which webapps are currently deployed
and update the scm consul record with the right details. This
script is expected to be called as part of withPipeline just
after spinInfra.
 --------------------------------------------------------------*/

// ConsulRecord class used to build requests to update consul records

class ConsulRecord {
  String ID
  String Name
  List Tags
  String Address
  Integer Port
}

def call(environment) {

// Get Auth Token
  OkHttpClient client = new OkHttpClient()

  MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded")
  RequestBody body = RequestBody.create(mediaType, "grant_type=client_credentials&resource=https%3A%2F%2Fmanagement.azure.com%2F&client_id=" + env.ARM_CLIENT_ID + "&client_secret=" + env.ARM_CLIENT_SECRET)
  Request request = new Request.Builder()
    .url("https://login.microsoftonline.com/" + env.ARM_TENANT_ID + "/oauth2/token")
    .post(body)
    .addHeader("content-type", "application/x-www-form-urlencoded")
    .addHeader("cache-control", "no-cache")
    .build()

  Response response = client.newCall(request).execute()


  def responsebody = new JsonSlurper().parseText(response.body().string())
  def authtoken = responsebody.access_token

// Get ServerFarms list

  Request requestfarms = new Request.Builder()
    .url("https://management.azure.com/subscriptions/" + env.ARM_SUBSCRIPTION_ID + "/resourceGroups/core-infra-" + environment + "/providers/Microsoft.Web/hostingEnvironments/core-compute-" + environment + "/serverfarms?api-version=2016-09-01")
    .get()
    .addHeader("authorization", "Bearer " + authtoken)
    .addHeader("cache-control", "no-cache")
    .build()

  Response responsefarms = client.newCall(requestfarms).execute()

  def responsefarmsbody = new JsonSlurper().parseText(responsefarms.body().string())
  List serverfarmslist = responsefarmsbody.value

// Get list of webapps using the ServerFarms list

  List webapps = []
  serverfarmslist.each {
    it.each {
      k, v -> if (k == "name") {
        webapps << v
      }
    }
  }

// Get ILB internal IP address

  Request requestilbip = new Request.Builder()
    .url("https://management.azure.com/subscriptions/" + env.ARM_SUBSCRIPTION_ID + "/resourceGroups/core-infra-" + environment + "/providers/Microsoft.Web/hostingEnvironments/core-compute-" + environment + "/capacities/virtualip?api-version=2016-09-01")
    .get()
    .addHeader("authorization", "Bearer " + authtoken)
    .addHeader("cache-control", "no-cache")
    .build()

  Response responseilbip = client.newCall(requestilbip).execute()

  def responseilbipbody = new JsonSlurper().parseText(responseilbip.body().string())
  String ilbinternalip = responseilbipbody.internalIpAddress


// Get details about the consul vm scale set i.e. IP address

  Request requestvmss = new Request.Builder()
    .url("https://management.azure.com/subscriptions/" + env.ARM_SUBSCRIPTION_ID + "/resourceGroups/core-infra-" + environment + "/providers/Microsoft.Compute/virtualMachineScaleSets/consul-server/networkInterfaces?api-version=2017-03-30")
    .get()
    .addHeader("Authorization", "Bearer " + authtoken)
    .addHeader("Cache-Control", "no-cache")
    .build();

  Response responsevmss = client.newCall(requestvmss).execute();

  def consulvmscaleset = new JsonSlurper().parseText(responsevmss.body().string())
  String consulapiaddr = consulvmscaleset.value[0].properties.ipConfigurations[0].properties.privateIPAddress


// Build json payload for scm record

  def scm = new ConsulRecord(ID: 'scm', Name: 'scm', Tags: webapps, Address: ilbinternalip, Port: 443 )
  def scmjson = new JsonBuilder(scm).toString()
  println("Preparing and sending scm consul record: " + scmjson)

// Update the scm record in consul

  MediaType mediaTypeScm = MediaType.parse("application/json");
  RequestBody scmrequestbody = RequestBody.create(mediaTypeScm, scmjson);
  Request requestscm = new Request.Builder()
    .url("http://" + consulapiaddr + ":8500/v1/agent/service/register")
    .post(scmrequestbody)
    .addHeader("content-type", "application/json")
    .addHeader("cache-control", "no-cache")
    .build();

  Response responsescm = client.newCall(requestscm).execute();

// Print the new record details

  println("Result code for scm service registration: " + responsescm.code())
}

