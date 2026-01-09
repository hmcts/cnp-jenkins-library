package uk.gov.hmcts.contino

interface JenkinsStepMock {
  String GITHUB_PROTOCOL = 'https'
  String TOKEN = "y5jgt49t5j4t50rk2]43"
  String GITHUB_REPO = 'github.com/contino/moj-module-redis/'

  HashMap getEnv()
  HashMap getCurrentBuild()

  Object sh(String)
  Object echo(String)
  Object error(String)
  Object git(Map params)
  Object tool(HashMap)
  String libraryResource(String)
  Object writeFile(Map)
  Object withCredentials(ArrayList, Closure)
  Object withAzureKeyvault(ArrayList, Closure)
  Object httpRequest(LinkedHashMap)

  Object ansiColor(String, Closure)
  Object fileExists(String)
  Object junit(String)
  Object archiveArtifacts(String)
  Object withDocker(String image, String options, Closure)
  Object gatlingArchive()
  Object withSauceConnect(String, Closure)
  Object saucePublisher()
  Object retry(Integer, Closure)
  Object readYaml(LinkedHashMap)
  String readFile(String)
  Object usernamePassword(Map)
  Object publishHTML(Map)
  Object fortifyOnDemandScan()
  Object fortifyOnDemandScan(Map)

  void azureCosmosDBCreateDocument(LinkedHashMap)
}


