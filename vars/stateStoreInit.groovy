#!groovy

//can be run only inside withSubscription
def call(String environment, String subscription, String deploymentTarget = "") {
  def functions = libraryResource 'uk/gov/hmcts/contino/stateStoreInit.sh'
  writeFile file: 'stateStoreInit.sh', text: functions

  __location = 'uksouth'
  __rg = "${env.STORE_rg_name_template}-${subscription}"
  sa_name = "${env.STORE_sa_name_template}${subscription}"
  sacontainer_name = "${env.STORE_sa_container_name_template}${environment}${deploymentTarget}"

  sh "bash stateStoreInit.sh $__rg $sa_name $sacontainer_name $__location $subscription"
}
