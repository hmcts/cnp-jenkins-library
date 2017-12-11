import uk.gov.hmcts.contino.*

//can be run only inside withSubscription
def call(String env) {

  def functions = libraryResource 'uk/gov/hmcts/contino/stateStoreInit.sh'
  writeFile file: 'stateStoreInit.sh', text: functions

  __location = 'uksouth'
  __rg= env.STORE_rg_name_template+ "-"+ env
  sa_name= STORE_sa_name_template+ env
  sacontainer_name = STORE_sa_container_name_template+ env

  sh "bash stateStoreInit.sh $__rg $sa_name $sacontainer_name $__location"
}
