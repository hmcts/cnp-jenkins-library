package uk.gov.hmcts.contino

class DotNetBuilder extends AbstractBuilder {

  def product

  DotNetBuilder(steps, product) {
    super(steps)
    this.product = product
  }

  def getBuildLabel() {
    return 'windows'
  }

  def build() {
    steps.powershell '''
    $Path = Resolve-Path **\\**.sln
    $proc = Start-Process -NoNewWindow -PassThru -FilePath nuget -ArgumentList "restore", "$Path"
    $proc.WaitForExit()
    '''
    //steps.bat "${tool 'MSBuild'} HearingsAPI\\HearingsAPI.sln /p:Configuration=Release /p:Platform=\"Any CPU\" /p:ProductVersion=1.0.0.${env.BUILD_NUMBER}"

    steps.powershell '''
    $Path = Resolve-Path **\\**.sln
    if (Test-Path $Path) {
        try {
            $proc = Start-Process -NoNewWindow -PassThru -FilePath msbuild -ArgumentList "$Path /t:rebuild  /fileLogger /p:Configuration=Release /p:ProductVersion=${env.BUILD_NUMBER}"
            $proc.WaitForExit()    
        }
        catch {
            throw $Error
        }
        if ($LASTEXITCODE -gt 0) {
            Write-Output "Build failed!"
            Break
        }
    }
    else {
        Write-Output "Solution not found!"
        break 
    }
     '''
    //steps.msbuild "\"${tool 'MSBuild'}\" HearingsAPI\\HearingsAPI.sln /p:Configuration=Release /p:Platform=\"Any CPU\" /p:ProductVersion=1.0.0.${env.BUILD_NUMBER}"
  }

  def test() {
    try {
      //gradle("--info check")
    } finally {
      //steps.junit '**/test-results/**/*.xml'
    }
  }

  def sonarScan() {
      //gradle("--info sonarqube")
  }

  def smokeTest() {
    try {
      // By default Gradle will skip task execution if it's already been run (is 'up to date').
      // --rerun-tasks ensures that subsequent calls to tests against different slots are executed.
      //gradle("--info --rerun-tasks smoke")
    } finally {
      //steps.junit '**/test-results/**/*.xml'
    }
  }

  def functionalTest() {
    try {
      // By default Gradle will skip task execution if it's already been run (is 'up to date').
      // --rerun-tasks ensures that subsequent calls to tests against different slots are executed.
      //gradle("--info --rerun-tasks functional")
    } finally {
      //steps.junit '**/test-results/**/*.xml'
    }
  }

  def apiGatewayTest() {
    try {
      // By default Gradle will skip task execution if it's already been run (is 'up to date').
      // --rerun-tasks ensures that subsequent calls to tests against different slots are executed.
      //gradle("--info --rerun-tasks apiGateway")
    } finally {
      //steps.junit '**/test-results/**/*.xml'
    }
  }

  def crossBrowserTest() {
    // this method is included in builder interface as part of nightly pipieline job
  }

  def securityCheck() {
    /*steps.withCredentials([steps.usernamePassword(credentialsId: 'owasp-db-login', passwordVariable: 'OWASPDB_ACCOUNT', usernameVariable: 'OWASPDB_PASSWORD')]) {
      try {
        gradle("-DdependencyCheck.failBuild=true -DdependencyCheck.cveValidForHours=24 -Danalyzer.central.enabled=false -DdependencyCheck.data.driver='com.microsoft.sqlserver.jdbc.SQLServerDriver' -DdependencyCheck.data.connectionString='jdbc:sqlserver://owaspdependencycheck.database.windows.net:1433;database=owaspdependencycheck;user=${steps.env.OWASPDB_ACCOUNT}@owaspdependencycheck;password=${steps.env.OWASPDB_PASSWORD};encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30;' -DdependencyCheck.data.username='${steps.env.OWASPDB_ACCOUNT}' -DdependencyCheck.data.password='${steps.env.OWASPDB_PASSWORD}' dependencyCheckAnalyze")
      }
      finally {
        steps.archiveArtifacts 'build/reports/dependency-check-report.html'
      }
    }*/
  }

  @Override
  def addVersionInfo() {
                  /*steps.sh '''
              mkdir -p src/main/resources/META-INF
              echo "allprojects { task printVersionInit { doLast { println project.version } } }" > init.gradle

              tee src/main/resources/META-INF/build-info.properties <<EOF 2>/dev/null
              build.version=$(./gradlew --init-script init.gradle -q :printVersionInit)
              build.number=${BUILD_NUMBER}
              build.commit=$(git rev-parse HEAD)
              build.date=$(date)
              EOF

              '''*/
  }

  def gradle(String task) {
    //steps.sh("./gradlew ${task}")
  }

  def dbMigrate(String vaultName, String microserviceName) {
    def az = { cmd -> return steps.sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$steps.env.SUBSCRIPTION_NAME az $cmd", returnStdout: true).trim() }

    def dbName = az "keyvault secret show --vault-name '$vaultName' --name '${microserviceName}-POSTGRES-DATABASE' --query value -o tsv"
    def dbHost = az "keyvault secret show --vault-name '$vaultName' --name '${microserviceName}-POSTGRES-HOST' --query value -o tsv"
    def dbPass = az "keyvault secret show --vault-name '$vaultName' --name '${microserviceName}-POSTGRES-PASS' --query value -o tsv"
    def dbPort = az "keyvault secret show --vault-name '$vaultName' --name '${microserviceName}-POSTGRES-PORT' --query value -o tsv"
    def dbUser = az "keyvault secret show --vault-name '$vaultName' --name '${microserviceName}-POSTGRES-USER' --query value -o tsv"

    //gradle("-Pdburl='${dbHost}:${dbPort}/${dbName}?ssl=true' -Pflyway.user='${dbUser}' -Pflyway.password='${dbPass}' migratePostgresDatabase")
  }
  
  @Override
  def mutationTest(){
    builder.mutationTest()
  }  

}
