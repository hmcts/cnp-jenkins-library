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

@Library("Infrastructure@opinionated-pipeline")

def type = "java"          // supports "java" and "nodejs"

def product = "rhubarb"

def app = "recipe-backend" // must match infrastructure module name

withPipeline(type, product, app) {
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
