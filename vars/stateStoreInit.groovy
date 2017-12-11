#!groovy
import uk.gov.hmcts.contino.*

//can be run only inside withSubscription
def call(String environment) {
  stage("Check/Init state store for env '${environment}'") {
    def functions = libraryResource 'uk/gov/hmcts/contino/stateStoreInit.sh'
//    writeFile file: 'stateStoreInit.sh', text: functions

    echo "got past writeFile"
    __location = 'uksouth'
    __rg = "${env.STORE_rg_name_template}-${environment}"
    sa_name = "${env.STORE_sa_name_template}${environment}"
    sacontainer_name = "${env.STORE_sa_container_name_template}${environment}"

    echo "before running bash"
    sh "bash stateStoreInit.sh $__rg $sa_name $sacontainer_name $__location"
  }
}
