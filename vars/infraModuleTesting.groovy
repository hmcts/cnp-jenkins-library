def call () {

  stage('Terraform Init') {
    sh 'terraform init'
  }

  stage('Terraform Linting Checks') {
    sh 'terraform validate -no-color'
  }

  stage('Integration tests') {
    withSubscription('sandbox') {
      sh '''
        scl enable rh-ruby24 "
        gem install bundler
        export PATH=$PATH:/home/jenkinsssh/bin/
        export TF_WARN_OUTPUT_ERRORS=1
        bundle install --path vendor/bundle
        cd tests/int
        bundle exec kitchen test
        "
      '''
    }
  }
}
