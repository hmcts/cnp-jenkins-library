package uk.gov.hmcts.contino

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/**
 * Helper class for creating test users in IDAM via the testing-support API.
 *
 * This client creates test users for performance testing in AAT and Preview
 * environments (both use AAT IDAM).
 *
 * Recommended usage:
 *   Use enableIdamTestUser() in your pipeline configuration - this automatically calls
 *   createIdamTestUsers() which uses this client.
 *
 * The createTestUser() method is idempotent - if the user already exists (409 Conflict),
 * the build continues and returns the existing user details.
 */
class IdamTestUserClient implements Serializable {

  def steps

  IdamTestUserClient(steps) {
    this.steps = steps
  }

  def createTestUser(String email, String forename, String surname, String password, List<String> roles) {

    // Validate required parameters
    if (!email?.trim()) {
      steps.error("email parameter is required and cannot be empty")
    }
    if (!forename?.trim()) {
      steps.error("forename parameter is required and cannot be empty")
    }
    if (!surname?.trim()) {
      steps.error("surname parameter is required and cannot be empty")
    }
    if (!password?.trim()) {
      steps.error("password parameter is required and cannot be empty")
    }
    if (!roles || roles.isEmpty()) {
      steps.error("roles parameter is required and must contain at least one role")
    }

    // Get endpoint from KeyVault secret (set via environment variable)
    def idamTestSupportUrl = steps.env.IDAM_TEST_SUPPORT_URL

    if (!idamTestSupportUrl?.trim()) {
      steps.error("IDAM_TEST_SUPPORT_URL environment variable not set. Ensure KeyVault secret 'idam-test-support-url' is loaded before calling createTestUser().")
    }

    // Build roles array in format required by IDAM
    def rolesArray = roles.collect { roleCode ->
      [code: roleCode]
    }

    // Build request body
    def requestBodyMap = [
      email: email,
      forename: forename,
      surname: surname,
      password: password,
      roles: rolesArray
    ]

    def requestBody = JsonOutput.toJson(requestBodyMap)

    steps.echo "Creating IDAM test user: ${email}"
    steps.echo "Roles: ${roles.join(', ')}"

    try {
      def response = steps.httpRequest(
        url: idamTestSupportUrl,
        httpMode: 'POST',
        contentType: 'APPLICATION_JSON',
        requestBody: requestBody,
        validResponseCodes: '200:299,409',
        consoleLogResponseBody: false  // Don't log response which may contain sensitive data
      )

      // Check if user already exists (409 Conflict)
      if (response.status == 409) {
        steps.echo "User already exists: ${email} - continuing with existing user"

        // Return basic user info (IDAM may not return full details on conflict)
        return [
          email: email,
          forename: forename,
          surname: surname,
          roles: rolesArray,
          existed: true
        ]
      }

      steps.echo "Test user created successfully: ${email}"

      // Parse and return response
      def responseData = new JsonSlurper().parseText(response.content)
      responseData.existed = false
      return responseData

    } catch (Exception e) {
      steps.echo "Failed to create test user: ${email}"
      steps.echo "Error: ${e.message}"
      throw e
    }
  }

}
