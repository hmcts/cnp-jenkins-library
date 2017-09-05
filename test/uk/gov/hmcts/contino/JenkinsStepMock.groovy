package uk.gov.hmcts.contino

interface JenkinsStepMock {
  String GITHUB_PROTOCOL = 'https'
  String TOKEN = "y5jgt49t5j4t50rk2]43"
  String GITHUB_REPO = 'github.com/contino/moj-module-redis/'

  Object sh(String)
  Object git(Map params)
  Object tool(HashMap)
  HashMap getEnv()
  String libraryResource(String)
  Object withCredentials(ArrayList, Closure)

}



