/**
 * Git Checkout Stage
 *
 * @param args arguments:
 *  <ul>
 *      <li>url - (string; required) the URL for the repository to checkout
 *  </ul>
 */
def call(String url) {

  stage('Checkout') {
    deleteDir()
    git([url   : url,
         branch: env.CHANGE_BRANCH])
  }

}
