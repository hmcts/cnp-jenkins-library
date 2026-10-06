#!groovy

//can be run only inside withSubscription
def call(String environment, String subscription, String deploymentTarget = "") {
  def functions = libraryResource 'uk/gov/hmcts/contino/stateStoreInit.sh'
  writeFile file: 'stateStoreInit.sh', text: functions

  def location = 'uksouth'
  def resourceGroup = "${env.STORE_rg_name_template}-${subscription}"
  def storageAccountName = "${env.STORE_sa_name_template}${subscription}"
  def storageContainerName = "${env.STORE_sa_container_name_template}${environment}${deploymentTarget}"

  sh "bash stateStoreInit.sh $resourceGroup $storageAccountName $storageContainerName $location $subscription"
}
