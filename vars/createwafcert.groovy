import uk.gov.hmcts.contino.*

def call() {

  stage("Create WAF certificate") {
    echo "Running SSL certificate creation script"
    String pfxPass = org.apache.commons.lang.RandomStringUtils.random(9, true, true)
    env.TF_VAR_pfxPass = "${pfxPass}"
    def functions = libraryResource 'uk/gov/hmcts/contino/createwafcert.sh'
    writeFile file: 'createwafcert.sh', text: functions

    result = sh "bash createwafcert.sh ${pfxPass}"
  }
}
