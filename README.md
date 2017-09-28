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
