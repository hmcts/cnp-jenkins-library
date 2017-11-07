import uk.gov.hmcts.contino.*

def call(String appName) {
  echo "Running SSL certificate creation script"
  String pfxPass = org.apache.commons.lang.RandomStringUtils.random(9, true, true)

  def functions = libraryResource 'uk/gov/hmcts/contino/createwafcert.sh'
  writeFile file: 'createwafcert.sh', text: functions

  result = sh "bash createwafcert.sh ${pfxPass}"

  sh '''
  echo "pfxPass = \"${pfxPass}\" > terraform.tfvars"
    '''
}
