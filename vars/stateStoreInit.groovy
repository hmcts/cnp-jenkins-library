import uk.gov.hmcts.contino.*

def call(String env) {

  def functions = libraryResource 'uk/gov/hmcts/contino/stateStoreInit.sh'
  writeFile file: 'stateStoreInit.sh', text: functions

  __statestore=readSecret("cfg-state-store")

  sa_rg_name= __statestore.rg_name+ "-"+ env
  sa_name= __statestore.sa_name+ env
  sacontainer_name = __statestore.sa_container_name+env

  sh "bash stateStoreInit.sh $sa_rg_name $sa_name $sacontainer_name uksouth"
  echo "$result"
}
