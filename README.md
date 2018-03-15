#Testing - Do not merge Test Test blah vbababvqa
test

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
* Deploy Prod
* Smoke Tests - Production

In this version, Java apps must be use Gradle for builds and contain the `gradlew` wrapper
script and dependencies in source control. NodeJS apps must use Yarn.

Example `Jenkinsfile` to use the opinionated pipeline:
```groovy
#!groovy
properties(
  [[$class: 'GithubProjectProperty', projectUrlStr: 'https://github.com/contino/moj-rhubarb-recipes-service'],
   pipelineTriggers([[$class: 'GitHubPushTrigger']])]
)

@Library("Infrastructure")

def type = "java"          // supports "java" and "nodejs"

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
```groovy
List<LinkedHashMap<String, Object>> secrets = [
  secret('secretNameInVault', 'theEnvVarYouWantToBeSet')
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

## Application specific infrastructure
It is possible for applications to build their specific infrastructure elements by providing `infrastructure` folder in application home directory containing terraform scripts to build that

In case your infrastructure includes database creation there is a Flyway migration step available that will be triggered only if it's enabled inside `withPipeline` block via `enableDbMigration()` function. By default this step is disabled

## Building and Testing
This is a Groovy project, and gradle is used to build and test.

Run
```bash
$ ./gradlew build
$ ./gradlew test
```

## Contributing

 1. Use the Github pull requests to make change
 2. Test the change by pointing a build, to the branch with the change
