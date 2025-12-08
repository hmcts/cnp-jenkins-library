package uk.gov.hmcts.pipeline

class DeprecationConfig {
    def steps
    static def deprecationConfigInternal

    DeprecationConfig(steps) {
        this.steps = steps
    }

    def getDeprecationConfig(String repoUrl = null) {
        if (deprecationConfigInternal == null) {
            def response = steps.httpRequest(
                consoleLogResponseBody: true,
                timeout: 10,
                url: "https://raw.githubusercontent.com/hmcts/cnp-deprecation-map/master/nagger-versions.yaml",
                validResponseCodes: '200'
            )
            deprecationConfigInternal = steps.readYaml(text: response.content)
        }

        // If no repoUrl is passed, return the raw deprecation configuration
        if (!repoUrl) {
            return deprecationConfigInternal
        }

        // Normalize repoUrl: trim, toLowerCase, and remove trailing ".git" if present.
        def normalizedRepoUrl = repoUrl.trim().toLowerCase().replaceAll(/\.git$/, "")

        // Process exceptions only when a repoUrl is provided
        def configWithExceptions = deprecationConfigInternal.clone()
        configWithExceptions.each { key, dependencies ->
            dependencies.each { dependency, details ->
                def exceptions = details.exceptions ?: []
                exceptions.each { exception ->
                    // Normalize exception repo to lowercase and remove trailing ".git" before comparison
                    def normalizedExceptionRepo = exception.repo.trim().toLowerCase().replaceAll(/\.git$/, "")
                    if (normalizedRepoUrl == normalizedExceptionRepo) {
                        details.date_deadline = exception.date_deadline
                    }
                }
            }
        }

        return configWithExceptions
    }
}
