
def call(String subscription, Closure body) {
  withSubscription(subscription) {
    sudo yum install -y rh-ruby24-ruby-devel
    scl enable rh-ruby24 "gem install bundler && export PATH=$PATH:/home/jenkinsssh/bin/; bundle install --path vendor/bundle; cd tests/int; bundle exec kitchen test"
  }
}
