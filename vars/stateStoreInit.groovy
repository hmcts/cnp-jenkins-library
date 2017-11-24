import uk.gov.hmcts.contino.*

def call(String env) {

  def functions = libraryResource 'uk/gov/hmcts/contino/stateStoreInit.sh'
  writeFile file: 'stateStoreInit.sh', text: functions

  __statestore= readSecret("cfg-state-store")
  __location = 'uksouth'
  __rg= __statestore.rg_name+ "-"+ env
  sa_name= __statestore.sa_name+ env
  sacontainer_name = __statestore.sa_container_name+ env

  sh "bash stateStoreInit.sh $__rg $sa_name $sacontainer_name $__location"
  echo "$result"

  //  check if the storage account exists. Creates it if not.
/*
  if  (! sh("az group exists --name $sa_rg_name", returnStdout: true).toBoolean()) {

    __isCreated = sh("az group create --name $__rg --location $__location --output json")
    __isCreated = "$(az group create --name $__rg --location $__location --output json | jq -r .properties.provisioningState)"

    if
    ["${__isCreated}" == "Succeeded"]; then
    echo "The resource $__rg has been created with no error"
    else
    fail "The resource group $__rg hasn't been created successfully"
    return 0
  }
  fi
  else
  echo "The resource $__rg already exists."
  fi
*/

}
