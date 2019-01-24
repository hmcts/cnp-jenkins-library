# Shared Jenkins Library for Code and Infrastructure pipelines

## How is this used?
Code in this library are loaded at runtime by Jenkins.
Jenkins is already configured to point to this repository
See [Jenkins Shared Libraries](https://jenkins.io/doc/book/pipeline/shared-libraries/)

In your pipeline, import this library.

```groovy
  @Library('Infrastructure')
```

To refer to a branch use
```groovy
@Library('Infrastructure@<branch-name>')
```

### Opinionated app pipeline

This library contains a complete opinionated pipeline that can build, test and deploy Java
and NodeJS applications. The pipeline contains the following stages:

* Checkout
* Build
* Unit Test
* Security Checks
* Lint (nodejs only)
* Sonar Scan
* Deploy Dev
* Smoke Tests - Dev
* (Optional) API (gateway) Tests - Dev
* Deploy Prod
* Smoke Tests - Production
* (Optional) API (gateway) Tests - Production

In this version, Java apps must use Gradle for builds and contain the `gradlew` wrapper
script and dependencies in source control. NodeJS apps must use Yarn.

Example `Jenkinsfile` to use the opinionated pipeline:
```groovy
#!groovy

@Library("Infrastructure")

def type = "java"          // supports "java", "nodejs" and "angular"

def product = "rhubarb"

def component = "recipe-backend" // must match infrastructure module name

withPipeline(type, product, component) {
}
```

#### Slack notifications on failure / fixed
To enable slack notifications when the build fails or is fixed add the following:
```groovy
withPipeline(type, product, component) {
  enableSlackNotifications('#my-team-builds')
}
```

#### Secrets for functional / smoke testing
If your tests need secrets to run, e.g. a smoke test user for production then:

`${env}` will be replaced by the pipeline with the environment that it is being run in
```groovy
def secrets = [
  'your-app-${env}': [
    secret('idam-client-secret', 'IDAM_CLIENT_SECRET')
  ],
  's2s-${env}'      : [
    secret('microservicekey-your-app', 'S2S_SECRET')
  ]
]

static LinkedHashMap<String, Object> secret(String secretName, String envVar) {
  [ $class: 'AzureKeyVaultSecret',
    secretType: 'Secret',
    name: secretName,
    version: '',
    envVariable: envVar
  ]
}

withPipeline(type, product, component) {
  loadVaultSecrets(secrets)
}
```

##### Overriding vault environment

In some instances vaults from a different environment could be needed. This is for example the case when deploying to `preview` environments, which should use `aat` vaults.

When enabled, `${env}` will be replaced by the overridden vault environment.

```groovy
def vaultOverrides = [
  'preview': 'aat',
  'spreview': 'saat'
]

def secrets = [
  'your-app-${env}': [
    secret('idam-client-secret', 'IDAM_CLIENT_SECRET')
  ],
  's2s-${env}'      : [
    secret('microservicekey-your-app', 'S2S_SECRET')
  ]
]

static LinkedHashMap<String, Object> secret(String secretName, String envVar) {
  [ $class: 'AzureKeyVaultSecret',
    secretType: 'Secret',
    name: secretName,
    version: '',
    envVariable: envVar
  ]
}

withPipeline(type, product, component) {
  overrideVaultEnvironments(vaultOverrides)
  loadVaultSecrets(secrets)
}
```

#### tf ouput for functional / smoke testing
Any outputs you add to `output.tf` are available as environment variable which can be used in smoke and functional tests.

If your functional tests require an environmental variable S2S_URL you can pass it in to functional test by adding it as a `output.tf`
````
output "s2s_url" {
  value = "http://${var.s2s_url}-${local.local_env}.service.core-compute-${local.local_env}.internal"
}
````
this output will be transposed to Uppercase s2s_url => S2S_URL and can then be used by functional and smoke test.

#### Security Checks

Calls `yarn test:nsp` so this command must be implemented in package.json

#### Smoke tests

To check that the app is working as intended you should implement smoke tests which call your app and check that the appropriate response is received.
This should, ideally, check the entire happy path of the application. Currently, the pipeline only supports Yarn to run smoketests and will call `yarn test:smoke`
so this must be implemented as a command in package.json. The pipeline exposes the appropriate application URL in the
`TEST_URL` environment variable and this should be used by the smoke tests you implement. The smoke test stage is
called after each deployment to each environment.

The smoke tests are to be non-destructive (i.e. have no data impact, such as not creating accounts) and a subset of component level functional tests.

#### Extending the opinionated pipeline

It is not possible to remove stages from the pipeline but it is possible to _add_ extra steps to the existing stages.

You can use the `before(stage)` and `after(stage)` within the `withPipeline` block to add extra steps at the beginning or end of a named stage. Valid values for the `stage` variable are

 * checkout
 * build
 * test
 * securitychecks
 * sonarscan
 * deploy:dev
 * smoketest:dev
 * deploy:prod
 * smoketest:prod

E.g.

```groovy
withPipeline(type, product, component) {
  after('checkout') {
    echo 'Checked out'
  }

  after('build') {
    sh 'yarn setup'
  }
}
```

#### API (gateway) tests

If your service contains an API (in Azure Api Management Service), you need to implement
tests for that API. For the pipeline to run those tests, do the following:

 - define `apiGateway` task (gradle/yarn) in you application
 - from your Jenkinsfile_CNP/Jenkinsfile_parameterized instruct the pipeline to run that gradle task:

  ```
  withPipeline(type, product, component) {
    ...
    enableApiGatewayTest()
    ...
  }
  ```

The API tests run after smoke tests.

## Application specific infrastructure
It is possible for applications to build their specific infrastructure elements by providing `infrastructure` folder in application home directory containing terraform scripts to build that

In case your infrastructure includes database creation there is a Flyway migration step available that will be triggered only if it's enabled inside `withPipeline` block via `enableDbMigration()` function. By default this step is disabled

## Azure Web Jobs
[Documentation from Azure](https://docs.microsoft.com/en-us/azure/app-service/web-sites-create-web-jobs)

If you want to create a Web Job for your app you need to create the following directory structure in the root of your project:

`App_Data\jobs\{job type}\{job name}`

* `job type` - Either *continuous* or *triggered*
* `job name` - The name of your Web Job

Within your job folder create a file called `run.<supported extention>`. Other files may be present but this is the file Azure will look to contain the runnable job.

For triggered jobs with a schedule, you can add a file called `settings.job` with a cron string like so:

```json
{
  "schedule": "0 30 0 * * *"
}
```

> Note: This has only been tested for Java applications!

## Building and Testing
This is a Groovy project, and gradle is used to build and test.

Run
```bash
$ ./gradlew build
$ ./gradlew test
```

Alternatively, you can use the gradle tasks from within a container using the following script:

```bash
$ ./start-docker-groovy-env
```

Then you can run the build and test tasks as described above.

## Contributing

 1. Use the Github pull requests to make change
 2. Test the change by pointing a build, to the branch with the change
