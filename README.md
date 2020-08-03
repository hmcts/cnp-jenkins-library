# Shared Jenkins Library for Code and Infrastructure pipelines

## How is this used?
Code in this library are loaded at runtime by Jenkins.
Jenkins is already configured to point to this repository
See [Jenkins Shared Libraries](https://jenkins.io/doc/book/pipeline/shared-libraries/)

In your pipeline, import this library.

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
* Smoke Tests - Dev
* (Optional) API (gateway) Tests - Dev
* Deploy Prod
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

Some basic versions of tools are installed on the [Jenkins agent VM images](https://github.com/hmcts/cnp-vm-hardening/blob/master/jenkins-agent-centos-7.4-x86_64.json) but we try to use version managers where possible, so that applications can update independently and aren't stuck using old versions forever.

### Java
Java 8 and Java 11 are installed directly on the Jenkins agent, the library will detect if Java 11 is in use by looking at the build.gradle file, if it can't detect it or it isn't Java 11 it will use Java 8

### Node.JS
[nvm](https://github.com/nvm-sh/nvm) is used, place a `.nvmrc` file at the root of your repo containing the version you want. If it isn't present we fallback to whatever is on the Jenkins agent, currently the latest 8.x version.

### Terraform
[tfenv](https://github.com/tfutils/tfenv) is used, place a `.terraform-version` file in your infrastructure folder for app pipelines, and at the root of your repo for infra pipelines. If this file isn't present we fallback to v0.11.7.

## Contract testing with Pact

#### Usage

You can activate contract testing lifecycle hooks in the CI using the `enablePactAs()` method.

The different hooks are based on roles that you can assign to your project: `CONSUMER` and/or `PROVIDER`. A common broker will be used as well as the naming and tagging conventions.

Here is an example of a project which acts a consumer and provider (for example a backend-for-frontend):

```groovy
import uk.gov.hmcts.contino.AppPipelineDsl

/* … */

withPipeline(product) {

  /* … */

  enablePactAs([
    AppPipelineDsl.PactRoles.CONSUMER,
    AppPipelineDsl.PactRoles.PROVIDER
  ])
}
```

The following hooks will then be ran before the deployment:

| Role       | Order | Yarn                           | Gradle                                      | Active on branch |
| ---------- | ----- | ------------------------------ | ------------------------------------------- | ---------------- |
| `PROVIDER` | 1     | `test:pact:verify-and-publish` | `runProviderPactVerification publish true`  | `master` only    |
| `PROVIDER` | 1     | `test:pact:verify`             | `runProviderPactVerification publish false` | Any branch       |
| `CONSUMER` | 2     | `test:pact:run-and-publish`    | `runAndPublishConsumerPactTests`            | Any branch       |

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

## Contributing

 1. Use the Github pull requests to make change
 2. Test the change by pointing a build, to the branch with the change
