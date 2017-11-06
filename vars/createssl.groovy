import uk.gov.hmcts.contino.*

def call(String appName) {
  echo "Running SSL certificate creation script"
  String pfxPass = org.apache.commons.lang.RandomStringUtils.random(9, true, true)
  domain=appName

  def functions = libraryResource 'uk/gov/hmcts/contino/create-cert.sh'
  writeFile file: 'create-cert.sh', text: functions

  result = sh "bash create-cert.sh ${product} ${pfxPass}"
}
