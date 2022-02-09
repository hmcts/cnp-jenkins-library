# Shared Jenkins Library for Code and Infrastructure pipelines

## How is this used?
Code in this library are loaded at runtime by Jenkins.
Jenkins is already configured to point to this repository.
See [Jenkins Shared Libraries](https://jenkins.io/doc/book/pipeline/shared-libraries/)

To get an understanding of the directory structure within this repository, please refer to [Directory Structure](https://www.jenkins.io/doc/book/pipeline/shared-libraries/#directory-structure)

To use this pipeline in your repo, you must import it in a Jenkinsfile

```groovy
  @Library('Infrastructure')
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
* Docker build (for AKS deployments, optional ACR steps)
* Contract testing
* Deploy Dev
* High Level Data Setup - Dev
* Smoke Tests - Dev
* (Optional) API (gateway) Tests - Dev
* Deploy Prod
* High Level Data Setup - Production
* Smoke Tests - Production
* (Optional) API (gateway) Tests - Production

In this version, Java apps must use Gradle for builds and contain the `gradlew` wrapper
script and dependencies in source control. NodeJS apps must use Yarn.

The opinionated app pipeline supports Slack notifications when the build fails or is fixed - your team build channel should be provided.

Example `Jenkinsfile` to use the opinionated pipeline:
```groovy
#!groovy

@Library("Infrastructure")

def type = "java"          // supports "java", "nodejs" and "angular"

def product = "rhubarb"

def component = "recipe-backend" // must match infrastructure module name

withPipeline(type, product, component) {
  enableSlackNotifications('#my-team-builds')
}
```

The opinionated pipeline uses the following branch mapping to deploy applications to different environments.

Branch | Environment
--- | ---
`master` | `aat` then `prod`
`demo` | `demo`
`perftest` | `perftest`
PR branch| `preview` (ASE or AKS depending on your config)

#### Secrets for functional / smoke testing
If your tests need secrets to run, e.g. a smoke test user for production then:

`${env}` will be replaced by the pipeline with the environment that it is being run in. In order to use this feature you **must use single quotes** around your string to prevent Groovy from resolving the variable immediately.

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
  ...
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
  ...
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

#### High level data setup

This can be used to import data required for the application.
The most common example is importing a CCD definition, but data requirements of a similar nature can be included using the same functionality.
Smoke and functional tests in non-production environments will run after the import allowing automated regression testing of the change.

By adding `enableHighLevelDataSetup()` to the Jenkinsfile, `High Level Data Setup` stages will be added to the pipeline.

```
#!groovy

@Library("Infrastructure")

def type = "java"
def product = "rhubarb"
def component = "recipe-backend"

withPipeline(type, product, component) {
  enableHighLevelDataSetup()
}

```

The opinionated pipeline uses the following branch mapping to import definition files to different environments.

Branch | HighDataSetup Stage
--- | ---
`master` | `aat` then `prod`
`PR` | `aat`
`perftest` | `perftest`
`demo` | `demo`
`ithc` | `ithc`

#### Extending the opinionated pipeline

It is not possible to remove stages from the pipeline but it is possible to _add_ extra steps to the existing stages.

You can use the `before(stage)` and `after(stage)` within the `withPipeline` block to add extra steps at the beginning or end of a named stage. Valid values for the `stage` variable are as follows where `ENV` must be replaced by the short environment name

 * checkout
 * build
 * test
 * securitychecks
 * sonarscan
 * deploy:ENV
 * smoketest:ENV
 * functionalTest:ENV
 * buildinfra:ENV

E.g.

```groovy
withPipeline(type, product, component) {

  ...

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


#### Clear Helm Release on Successful Build

If your service never use the deployed resources once the build is green, teams can clear the helm release to free resources on the cluster. 

To clear helm release, do the following:

  ```
  withPipeline(type, product, component) {
    ...
    enableCleanupOfHelmReleaseOnSuccess()
    ...
  }
  ```

### Opinionated infrastructure pipeline

For infrastructure-only repositories e.g. "shared infrastructure" the library provides an opinionated infrastructure pipeline which will build Terraform files in the root of the repository.

The opinionated infrastructure pipeline supports Slack notifications when the build fails or is fixed - your team build channel should be provided.

It uses a similar branch --> environment strategy as the app pipeline but with some differences for PRs

Branch | Environment
--- | ---
`master` | `aat` then `prod`
`demo` | `demo`
`perftest` | `perftest`
PR branch| `aat` (plan only)


Example `Jenkinsfile` to use the opinionated infrastructure pipeline:
```groovy
#!groovy

@Library("Infrastructure") _

def product = "rhubarb"

withInfraPipeline(product) {

  enableSlackNotifications('#my-team-builds')

}
```

#### Optional parameters for the opinionated infratructure pipeline

You have the ability to pass extra parameters to the `withInfraPipeline`.

These parameters include:
| parameter name | description
| --- | --- | --- |
| component | https://hmcts.github.io/glossary/#component |

Example `Jenkinsfile` to use the opinionated infrastructure pipeline:
```groovy
#!groovy

@Library("Infrastructure") _

def product = "rhubarb"

//Optional
def component = "extra-detail" 

withInfraPipeline(product, component) {

  enableSlackNotifications('#my-team-builds')

}
```

#### Extending the opinionated infratructure pipeline

It is not possible to remove stages from the pipeline but it is possible to _add_ extra steps to the existing stages.

You can use the `before(stage)` and `after(stage)` within the `withInfraPipeline` block to add extra steps at the beginning or end of a named stage. Valid values for the `stage` variable are as follows where `ENV` should be replaced by the short environment name

 * checkout
 * buildinfra:ENV

E.g.

```groovy
withInfraPipeline(product) {

  ...

  after('checkout') {
    echo 'Checked out'
  }

  before('buildinfra:aat') {
    echo 'About to build infra in AAT'
  }
}
```

## Application specific infrastructure
It is possible for applications to build their specific infrastructure elements by providing `infrastructure` folder in application home directory containing terraform scripts to build that

In case your infrastructure includes database creation there is a Flyway migration step available that will be triggered only if it's enabled inside `withPipeline` block via `enableDbMigration()` function. By default this step is disabled

## Nightly pipeline

The intent of the Nightly Pipeline is to run dependency checks on a nightly basis against the AAT environment as well as some optional tests.

Example block to enable tests:
```
withNightlyPipeline(type, product, component) {

  // add this!
  enableCrossBrowserTest()
  enableFortifyScan()
}
```

Dependency checks are mandatory and will be included in all pipelines. The tests stages are all 'opt-in' and can be added or removed based on your needs.

All available test stages are detailed in the table below:

TestName | How to enable | Example
--- | --- | ---
 CrossBrowser | Add package.json file with "test:crossbrowser" : "Your script to run browser tests" and call enableCrossBrowserTest()| [CrossBrowser example](https://github.com/hmcts/nfdiv-frontend/blob/aea2aa8429d3c7495226ee6b5178bde6f0b639e4/package.json#L31)
 FortifyScan | Call enableFortifyScan() | [Java example](https://github.com/hmcts/ccd-user-profile-api/pull/409/files) <br>[Node example](https://github.com/hmcts/ccd-case-management-web/pull/1102/files)
 Performance* | Add Gatling config and call enablePerformancetest() | [Example Gatling config](https://github.com/hmcts/sscs-performance/tree/64168f527add681d8a2853791a0508b7997fbb1b/src/gatling)
 SecurityScan | Add a file in root of repository called security.sh and call enableSecurityScan() | [Web Application example](https://github.com/hmcts/probate-frontend/blob/a56b63fb306b6b2139148c27b7b1daf001f2743c/security.sh) <br>[API example](https://github.com/hmcts/document-management-store-app/blob/master/security.sh)
 Mutation | Add package.json file with "test:mutation": "Your script to run mutation tests" and call enableMutationTest() | [Mutation example](https://github.com/hmcts/pcq-frontend/blob/77d59f2143c91502bec4a1690609b5195cc78908/package.json#L30)
 FullFunctional | Call enableFullFunctionalTest() | [FullFunctional example](https://github.com/hmcts/nfdiv-frontend/blob/aea2aa8429d3c7495226ee6b5178bde6f0b639e4/Jenkinsfile_nightly#L48)

*Performance tests use Gatling. You can find more information about the tool on their website https://gatling.io/.

The current state of the Nightly Pipeline is geared towards testing both frontend and backend applications served by NodeJS, AngularJS and Java APIs.

The pipeline contains stages for application checkout, build and list of testing types. Jenkins triggers the build based on the Jenkins file configuration. In order to enable the Jenkins Nightly Pipeline, a file named `Jenkinsfile_nightly` must be included in the repository.

Create the `Jenkinsfile_Nightly`, import the Infrastructure library and use the `withNightlyPipeline` block.

When initially setting up the nightly pipeline for use in your repo, you should make use of the `nightly-dev` branch. You should also utilise this branch when debugging any issues that arise in the nightly pipeline.

#### Extending the test pipeline

You can use the `before(stage)` and `after(stage)` within the `withNightlyPipeline` block to add extra steps at the beginning or end of a named stage.
```
withNightlyPipeline(type, product, component) {
  enableCrossBrowserTest()
  enableFullFunctionalTest()
  loadVaultSecrets(secrets)

  before('crossBrowserTest') {
    yarnBuilder.smokeTest()
  }

  after('crossBrowserTest') {
    steps.archiveArtifacts allowEmptyArchive: true, artifacts: 'functional-output/crossbrowser/reports/**/*'
  }

  after('fullFunctionalTest') {
    steps.archiveArtifacts allowEmptyArchive: true, artifacts: 'functional-output/functional/reports/**/*'
  }
}
```

## Cron Jobs
You need to add `nonServiceApp()` method in `withPipeline` block to skip service specific steps in the pipeline.

```groovy
#!groovy

@Library("Infrastructure")

withPipeline(type, product, component) {
    nonServiceApp()
}
```

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

## Container build

If you use AKS deployments, a docker image is built and pushed remotely to ACR.

You can optionally make this build faster by using explicit ACR tasks, in a `acb.tpl.yaml` file located at the root of your project (watch out, the extension is .yaml, not .yml).

This is particularly effective for nodejs projecs pulling loads of npm packages.

Here is a sample file, assuming you use docker multi stage build:

```yaml
# ./acb.tpl.yaml
version: 1.0-preview-1
steps:
  # Pull previous build images
  # This is used to leverage on layers re-use for the next steps
  - id: pull-base
    cmd: docker pull {{.Run.Registry}}/product/component/base:latest || true
    when: ["-"]
    keep: true
  # (Re)create base image
  - id: base
    build: >
      -t {{.Run.Registry}}/product/component/base
      --cache-from {{.Run.Registry}}/product/component/base:latest
      --target base
      .
    when:
      - pull-base
    keep: true
  # Create runtime image
  - id: runtime
    build: >
      -t {{.Run.Registry}}/{{CI_IMAGE_TAG}}
      --cache-from {{.Run.Registry}}/product/component/base:latest
      --target runtime
      .
    when:
      - base
    keep: true
  # Push to registry
  - id: push-images
    push:
      - "{{.Run.Registry}}/product/component/base:latest"
      - "{{.Run.Registry}}/{{CI_IMAGE_TAG}}"
    when:
      - runtime
```

Properties expanded by Jenkins:

| Property matcher    |                                                                                                            |
| ------------------- | ---------------------------------------------------------------------------------------------------------- |
| `{{CI_IMAGE_TAG}}`  | is the stadard name of the runtime image                                                                   |
| `{{REGISTRY_NAME}}` | is the registry name, e.g. hmcts of hmctssandbox. Useful if you want to pass it as `--build-arg` parameter |


If you want to learn more about ACR tasks, [here is the documentation](https://docs.microsoft.com/en-gb/azure/container-registry/container-registry-tasks-reference-yaml).

## Tool versions

Some basic versions of tools are installed on the [Jenkins agent VM images](https://github.com/hmcts/jenkins-packer/blob/master/jenkins-agent-centos-7.4-x86_64.json) but we try to use version managers where possible, so that applications can update independently and aren't stuck using old versions forever.

### Java
Java 11 is installed on the Jenkins agent.

### Node.JS
[nvm](https://github.com/nvm-sh/nvm) is used, place a `.nvmrc` file at the root of your repo containing the version you want. If it isn't present we fallback to whatever is on the Jenkins agent, currently the latest 8.x version.

### Terraform
[tfenv](https://github.com/tfutils/tfenv) is used, place a `.terraform-version` file in your infrastructure folder for app pipelines, and at the root of your repo for infra pipelines. If this file isn't present we fallback to v0.11.7.

## Camunda Deployments from separate Camunda Process repo

#### Usage

You can activate the testing and deployment of Camunda files using the `withCamundaOnlyPipeline()` method
This particular method is designed to be used with a separate Camunda repo, as opposed to Camunda files in the app repo.
It has been configured to find BPMN and DMN files in the repo, and create the deployment in Camunda if there are changes.

It will run unit and security tests on PRs, and will upload these DMN/BPMN files to Camunda once merged.

Example of usage
```groovy

/* … */
def s2sServiceName = "wa_task_configuration_api"

withCamundaOnlyPipeline(type, product, component, s2sServiceName, tenantId) {
  /* … */
}
```

These s2s Service Names can be found in the camunda-bpm repo: https://github.com/hmcts/camunda-bpm/blob/d9024d0fe21592b39cd77fd6dbd5c2e585e56c59/src/main/resources/application.yaml#L58, eg. unspec-service, wa_task_configuration_api etc.

Tenant ID can also be checked from the camunda-bpm repo: https://github.com/hmcts/camunda-bpm/blob/master/src/main/resources/application.yaml#L47 eg. wa, ia, civil-unspecified etc.


## Contract testing with Pact

#### Usage

You can activate contract testing lifecycle hooks in the CI using the `enablePactAs()` method.

The different hooks are based on roles that you can assign to your project: `CONSUMER` and/or `PROVIDER` and/or 'CONSUMER_DEPLOY_CHECK' (to be used in conjunction with CONSUMER role). A common broker will be used as well as the naming and tagging conventions.

Here is an example of a project which acts a consumer and provider (for example a backend-for-frontend):

```groovy
import uk.gov.hmcts.contino.AppPipelineDsl

/* … */

withPipeline(product) {

  /* … */

  enablePactAs([
    AppPipelineDsl.PactRoles.CONSUMER,
    AppPipelineDsl.PactRoles.PROVIDER,
    AppPipelineDsl.PactRoles.CONSUMER_DEPLOY_CHECK
  ])
}
```

The following hooks will then be ran before the deployment:

| Role           | Order | Yarn                           | Gradle                                      | Active on branch |
| -------------- | ----- | ------------------------------ | ------------------------------------------- | ---------------- |
| `CONSUMER`     | 1     | `test:pact:run-and-publish`    | `runAndPublishConsumerPactTests`            | Any branch       |
| `PROVIDER`     | 2     | `test:pact:verify-and-publish` | `runProviderPactVerification publish true`  | `master` only    |
| `PROVIDER`     | 2     | `test:pact:verify`             | `runProviderPactVerification publish false` | Any branch       |
| `CONSUMER_DEPLOY_CHECK` | 3     | `test:can-i-deploy:consumer`   | `canideploy`                                | Any branch       |

#### Notes

The Pact broker url and other parameters are passed to these hooks as following:

- `yarn`:
  - `PACT_BROKER_URL`
  - `PACT_CONSUMER_VERSION`/`PACT_PROVIDER_VERSION`
- `gradlew`:
  - `-Dpact.broker.url`
  - `-Dpact.consumer.version`/`-Dpact.provider.version`
  - `-Dpact.verifier.publishResults=${onMaster}` is passed by default for providers

🛎️  `onMaster` is a boolean that is true if the current branch is `master`
🛎️  It is expected that the scripts are responsible for figuring out which tag or branch is currently tested.

## Keep environment specific branches in sync with master

#### Usage

The environment specific branches such as demo, ithc and perftest can be automatically synced with master branch.

This can be achieved by using the `syncBranchesWithMaster()` method in Application, Infrastructure and Camunda pipelines. This method will be invoked in the master build and execute as the last stage in the build.

Example of usage
```groovy

def branchesToSync = ['demo', 'perftest']

withPipeline(type, product, component) {
  syncBranchesWithMaster(branchesToSync)
}

```

## Import terraform modules created using template deployment to native Terraform resources
#### Usage

Terraform AzureRM provider now supports new resource types, which were previously created using Azure Template Deployment.

Currently, resources created using the following modules can be imported:

* Service Bus Namespace (https://github.com/hmcts/terraform-module-servicebus-namespace)
* Service Bus Topic (https://github.com/hmcts/terraform-module-servicebus-topic)
* Service Bus Queue (https://github.com/hmcts/terraform-module-servicebus-queue)
* Service Bus Subscription (https://github.com/hmcts/terraform-module-servicebus-subscription)

Platops have released new versions of these modules, where native terraform resource types are used. The new version is available in a separate branch in the respective repositories.

To consume the new modules, existing resources must be imported to the new module structure. The import will be automatically performed in the background if there are modules that needs to be imported. Users will notice a new stage "Import Terraform Modules" in the pipeline.

**NOTE:** The module's local name should **NOT** be changed for the import to work as expected. For example: ``` module "servicebus-namespace" { ... } ```. The local name "servicebus-namespace" should not be changed.

**Example:**

Build Console: https://sandbox-build.platform.hmcts.net/job/HMCTS_Sandbox_RD/job/rd-shared-infrastructure/job/sandbox/170/consoleFull

## Troubleshooting

Any steps that you see in your Jenkins pipeline can be found within this repository.

If you search this repository for the command being run when a failure occurs, you can see where the command and it's associated variables are defined.

For example, pipelines are restricted to create resources via Terraform that have been pre-approved.

If your pipeline fails with an error message saying "this repo is using a terraform resource that is not allowed", you can search the repo for this message to see where the steps that throw this error are defined.

On searching for this, you will be directed to [/vars/approvedTerraformInfrastructure.groovy](https://github.com/hmcts/cnp-jenkins-library/blob/48109489e4a1075196142f9c1022c38be1f52ddf/vars/approvedTerraformInfrastructure.groovy#L47)

This file calls a class named [TerraformInfraApprovals](https://github.com/hmcts/cnp-jenkins-library/blob/48109489e4a1075196142f9c1022c38be1f52ddf/src/uk/gov/hmcts/pipeline/TerraformInfraApprovals.groovy#L23).

This file will point to the repository which defines, in json syntax, which infrastructure resources and modules are approved for use at the [global](https://github.com/hmcts/cnp-jenkins-config/blob/master/terraform-infra-approvals/global.json) and [project](https://github.com/hmcts/cnp-jenkins-config/blob/master/terraform-infra-approvals/bulk-scan-shared-infrastructure.json) level.

## Contributing

 1. Use the Github pull requests to make change
 2. Test the change by pointing a build, to the branch with the change
