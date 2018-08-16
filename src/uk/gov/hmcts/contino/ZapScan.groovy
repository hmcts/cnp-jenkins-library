package uk.gov.hmcts.contino

class ZapScan implements Serializable {

  public static final String OWASP_ZAP_IMAGE    = 'owasp/zap2docker-stable:latest'
  public static final String OWASP_ZAP_ARGS     = 'zap-x.sh -daemon -host 127.0.0.1 -port 8080 -config api.addrs.addr.name=.* -config api.addrs.addr.regex=true -config api.disablekey=true'

  def steps

  ZapScan(steps) {
    this.steps = steps
  }

  def execute() {
    try{
    this.steps.withDocker(OWASP_ZAP_IMAGE, "--network=host --name=zaptest" + OWASP_ZAP_ARGS)
    this.steps.sh '''
            set -e
            echo ${TEST_URL}
            zap-cli --zap-url http://127.0.0.1 -p 8080 status -t 120
            zap-cli --zap-url http://127.0.0.1 -p 8080 open-url "${TEST_URL}"
            zap-cli --zap-url http://127.0.0.1 -p 8080 active-scan  --scanners all --recursive "${TEST_URL}"
            zap-cli --zap-url http://127.0.0.1 -p 8080 report -o security-reports/activescan.html -f html
            zap-cli --zap-url http://127.0.0.1 -p 8080 spider "${TEST_URL}"
            zap-cli --zap-url http://127.0.0.1 -p 8080 report -o security-reports/spider.html -f html
            zap-cli --zap-url http://127.0.0.1 -p 8080 ajax-spider "${TEST_URL}"
            zap-cli --zap-url http://127.0.0.1 -p 8080 report -o security-reports/ajax-spider.html -f html
            zap-cli --zap-url http://127.0.0.1 -p 8080 alerts -l Low
            cp zaptest:/zap/security-reports/**  ./functional-output/security-reports
              
          '''
   }
  finally{
     steps.sh ''' cp zaptest:/zap/security-reports/**  ../security-reports '''
     steps.archiveArtifacts allowEmptyArchive: true, artifacts: '**/security-reports/**/*'
    }
  }

}
