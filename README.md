# Shared Jenkins Library for Code and Infrastructure pipelines

## How is this used?
Code in this library are loaded at runtime by Jenkins.
Jenkins is already configured to point to this repository
See [Jenkins Shared Librareis](https://jenkins.io/doc/book/pipeline/shared-libraries/)

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
* Lint (nodejs only)
* Sonar Scan
* Security Checks
* NSP
* Deploy Dev
* Smoke Tests - Dev
* OWASP
* Deploy Prod
* Smoke Tests - Prod

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

def app = "recipe-backend" // must match infrastructure module name

withPipeline(type, product, app) {
}
```
#### Smoke tests

To check that the app is working as intended you should implement smoke tests which call your app and check that the appropriate response is received.
This should, ideally, check the entire happy path of the application. Currently, the pipeline only supports Yarn to run smoketests and will call `yarn test:smoke`
so this mus be implemented as a command in package.json. The pipeline exposes the appropriate application URL in the
`SMOKETEST_URL` environment variable and this should be used by the smoke tests you implement. The smoke test stage is
called after each deployment to each environment.

#### Extending the opinionated pipeline

It is not possible to remove stages from the pipeline but it is possible to _add_ extra steps to the existing stages.

You can use the `before(stage)` and `after(stage)` within the `withPipeline` block to add extra steps at the beginning or end of a named stage. Valid values for the `stage` variable are

 * checkout
 * build
 * test
 * sonarscan
 * deploy:dev
 * smoketest:dev
 * deploy:rod
 * smoketest:prod

E.g.

```
withPipeline(type, product, app) {
  after('checkout') {
    echo 'Checked out'
  }
  
  before('deploy:prod') {
    input 'Are you sure you want to deploy to production?'
  }
}
```

## Building and Testing
This is a Groovy project, and gradle is used to build and test.

Run
```bash
gradle build
gradle test
```

## Contributing

 1. Use the Github pull requests to make change
 2. Test the change by pointing a build, to the branch with the change
