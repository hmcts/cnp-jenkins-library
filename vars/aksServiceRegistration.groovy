@Grab('com.squareup.okhttp3:okhttp:3.9.1')
@Grab('com.squareup.okio:okio:1.13.0')

import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import okhttp3.OkHttpClient
import okhttp3.OkHttpClient.Builder
import java.util.concurrent.TimeUnit
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

def call(subscription, serviceName, serviceIP) {

  println "Registering AKS application to consul cluster in `$subscription` subscription"

  def environment
  switch (subscription) {
    case ['nonprod', 'prod']:
      environment = 'preview'
      break
    case 'sandbox':
      environment = 'saat'
      break
    default:
      environment = 'saat'
      break
  }

  // Get Auth Token
  println "Getting access token from management.azure.com ..."
  OkHttpClient client = new OkHttpClient.Builder()
    .connectTimeout(90, TimeUnit.SECONDS)
    .writeTimeout(90, TimeUnit.SECONDS)
    .readTimeout(90, TimeUnit.SECONDS)
    .build()

  MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded")
  RequestBody body = RequestBody.create(mediaType, "grant_type=client_credentials&resource=https%3A%2F%2Fmanagement.azure.com%2F&client_id=" + env.ARM_CLIENT_ID + "&client_secret=" + env.ARM_CLIENT_SECRET)
  Request requestToken = new Request.Builder()
    .url("https://login.microsoftonline.com/" + env.ARM_TENANT_ID + "/oauth2/token")
    .post(body)
    .addHeader("content-type", "application/x-www-form-urlencoded")
    .addHeader("cache-control", "no-cache")
    .build()

  Response responseToken = client.newCall(requestToken).execute()

  def responseTokenBody = new JsonSlurper().parseText(responseToken.body().string())
  def authtoken = responseTokenBody.access_token

// Get details about the consul vm scale set i.e. IP address
  println "Getting consul's IP address ..."
  Request requestLbCfg = new Request.Builder()
    .url("https://management.azure.com/subscriptions/" + env.ARM_SUBSCRIPTION_ID + "/resourceGroups/core-infra-" + environment + "/providers/Microsoft.Network/loadBalancers/consul-server_dns/frontendIPConfigurations/privateIPAddress?api-version=2018-04-01")
    .get()
    .addHeader("Authorization", "Bearer " + authtoken)
    .addHeader("Cache-Control", "no-cache")
    .build();

  Response responseLbCfg = client.newCall(requestLbCfg).execute();

  def consulLbCfg = new JsonSlurper().parseText(responseLbCfg.body().string())
  log.debug "Consul LB config: " + consulLbCfg
  String consulapiaddr = consulLbCfg.privateIPAddress
  println "Consul IP address is: " + consulapiaddr

  // Build json payload for scm record
  def serviceRecord = new ConsulRecord(ID: serviceName, Name: serviceName, Address: serviceIP, Port: 80 )
  def serviceRecordjson = new JsonBuilder(serviceRecord).toString()
  println("Sending consul record: " + serviceRecordjson)

  // Update the scm record in consul
  MediaType reqMediaType = MediaType.parse("application/json");
  RequestBody requestBody = RequestBody.create(reqMediaType, serviceRecordjson);
  Request request = new Request.Builder()
    .url("http://" + consulapiaddr + ":8500/v1/agent/service/register")
    .post(requestBody)
    .addHeader("content-type", "application/json")
    .addHeader("cache-control", "no-cache")
    .build();

  Response response = client.newCall(request).execute();

  // Print the new record details
  println("Result code for scm service registration: " + response.code())
}

