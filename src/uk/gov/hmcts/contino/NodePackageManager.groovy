package uk.gov.hmcts.contino

class NodePackageManager implements Serializable {

  def steps

  NodePackageManager(steps){

    this.steps = steps;
  }
  def install(){
    run "config set registry http://artifactory.reform.hmcts.net/artifactory/api/npm/npm-remote"
    run "install"
  }

  def test() {
    steps.sh  """
               export JUNIT_REPORT_PATH = "test-reports.xml"
               npm test --reporter mocha-jenkins-reporter --reporter-options junit_report_packages=true
              """
  }



  private def run(npmCommand){
    steps.sh "npm ${npmCommand}"
  }

}
