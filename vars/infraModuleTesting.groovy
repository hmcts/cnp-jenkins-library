def call {
  withSubscription('sandbox') {
    sh 'scl enable rh-ruby24 "gem install bundler && export PATH=$PATH:/home/jenkinsssh/bin/; bundle install --path vendor/bundle; cd tests/int; bundle exec kitchen test"'
  }
}
