import uk.gov.hmcts.contino.IdamTestUserClient
import uk.gov.hmcts.contino.Environment

/*============================================================================================
Creates a test user in IDAM for performance testing.

This function only runs in AAT and Preview environments (both use AAT IDAM).
In all other environments, it logs a message and returns null without creating a user.

Note: This is typically called automatically by dynatracePerformanceSetup when you use
enableIdamTestUser() in your pipeline configuration

@param params Map containing: email, forename, surname, password, roles
@return Created user details from IDAM, or null if not in AAT/Preview environment
=============================================================================================*/
def call(Map params = [:]) {

  // Check if we're in AAT or Preview environment
  def environment = new Environment(env)
  def currentEnv = environment.nonProdName

  if (currentEnv != 'aat' && currentEnv != 'preview') {
    echo "Skipping IDAM test user creation - only supported in AAT and Preview environments (current: ${currentEnv})"
    return null
  }

  echo "Environment is ${currentEnv} - proceeding with IDAM test user creation"

  // Validate required parameters
  if (!params.email) {
    error("email parameter is required")
  }
  if (!params.forename) {
    error("forename parameter is required")
  }
  if (!params.surname) {
    error("surname parameter is required")
  }
  if (!params.password) {
    error("password parameter is required")
  }
  if (!params.roles || params.roles.isEmpty()) {
    error("roles parameter is required and must contain at least one role")
  }

  // Validate that IDAM_TEST_SUPPORT_URL is loaded from KeyVault
  if (!env.IDAM_TEST_SUPPORT_URL) {
    error("IDAM_TEST_SUPPORT_URL environment variable not found. This should be loaded automatically by sectionDeployToAKS when enableIdamTestUser() is configured.")
  }

  echo "Using IDAM URL: ${env.IDAM_TEST_SUPPORT_URL}"

  def idamClient = new IdamTestUserClient(this)

  echo "Creating IDAM test user: ${params.email}"
  return idamClient.createTestUser(
    params.email,
    params.forename,
    params.surname,
    params.password,
    params.roles
  )
}
