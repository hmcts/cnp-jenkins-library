package uk.gov.hmcts.tests

interface JenkinsStepMock {


  Object sh(String)
  Object tool(HashMap)
  HashMap getEnv()
  Object withCredentials(Object)
}
