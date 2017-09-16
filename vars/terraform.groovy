def call(String command) {
  sh "echo 'terraform line 1'"
  sh "terraform ${command}"
}
