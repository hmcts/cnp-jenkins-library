import uk.gov.hmcts.pipeline.deprecation.WarningCollector
import uk.gov.hmcts.pipeline.DeprecationConfig
import uk.gov.hmcts.pipeline.LibraryBranchControls
import uk.gov.hmcts.pipeline.WarningBanner
import java.time.LocalDate

def call(String repoUrl = null) {
    if (shouldSkipChecks()) {
        return
    }

    def jenkinsLibraryDeprecationConfig = repoUrl ?
        new DeprecationConfig(this).getDeprecationConfig(repoUrl).jenkins :
        new DeprecationConfig(this).getDeprecationConfig().jenkins

    writeFile file: 'check-library-version.sh', text: libraryResource('uk/gov/hmcts/library/check-library-version.sh')
    writeFile file: 'warning-banner.txt', text: libraryResource(WarningBanner.RESOURCE_PATH)

    try {
        checkUnpinnedLibrary()
        checkAllowedLibraryBranches()
        checkOldLibraryVersions(jenkinsLibraryDeprecationConfig)
    } finally {
        sh 'rm -f check-library-version.sh'
        sh 'rm -f warning-banner.txt'
    }
}

def shouldSkipChecks() {
    String jobName = env.JOB_NAME?.toLowerCase()
    if (jobName?.contains('nightly')) {
        echo 'Skipping custom library version checks for nightly jobs.'
        return true
    }

    String subscriptionName = env.JENKINS_SUBSCRIPTION_NAME?.toLowerCase()
    if (subscriptionName?.contains('sbox') || subscriptionName?.contains('sandbox')) {
        echo 'Skipping custom library version checks on Sandbox Jenkins.'
        return true
    }

    return false
}

def buildAndAddWarning(int status, String warningKey, List<List<String>> paragraphs, String deprecationDeadline = null) {
    if (status == 0) {
        return
    }

    String warningMessage = paragraphs.collect { it.join(' ') }.join('\n\n')
    LocalDate deprecationDate = deprecationDeadline ? LocalDate.parse(deprecationDeadline) : null

    WarningCollector.addPipelineWarning(warningKey, warningMessage, deprecationDate)
}

def checkUnpinnedLibrary() {
    int unpinnedStatus = sh(
        script: """
        chmod +x check-library-version.sh
        ./check-library-version.sh 'unpinned'
        """,
        returnStatus: true
    )

    buildAndAddWarning(
        unpinnedStatus,
            "unpinned_infrastructure_library",
            [
                [
                    'Your Jenkinsfile is tracking the default branch for Infrastructure',
                    'via @Library("Infrastructure").'
                ],
                [
                    "This means the pipeline is following the repository's default branch (master),",
                    'not a fixed library version, so it can change unexpectedly when upstream library',
                    'updates are merged. Check for the latest release at',
                    'https://github.com/hmcts/cnp-jenkins-library/releases, then update to a fixed',
                    'library version to keep the pipeline stable.',
                    'Renovate can automatically update pinned library versions for you.'
                ]
            ]
    )
}

def checkAllowedLibraryBranches() {
    def allowedLibraryBranches = new LibraryBranchControls(this)
        .getLibraryBranchControls()
        .branches
        .findAll { it.allowed && it.name != 'master' && !(it.name ==~ /\d+\.\d+\.\d+/) }
        .collect { it.name }

    allowedLibraryBranches.each { branch ->
        int branchStatus = sh(
            script: """
            chmod +x check-library-version.sh
            ./check-library-version.sh 'branch' '${branch}'
            """,
            returnStatus: true
        )

        buildAndAddWarning(
            branchStatus,
                "allowed_infrastructure_library_branch",
                [
                    [
                        'Your Jenkinsfile is using an allowed Infrastructure library branch',
                        "*${branch}*."
                    ],
                    [
                        'Merge the changes you need from this branch, check for the latest release at',
                        'https://github.com/hmcts/cnp-jenkins-library/releases, then switch to a fixed',
                        'library version. Pinned versions receive the latest library features and reduce',
                        'the chance of upstream changes unexpectedly breaking your pipeline.',
                        'Renovate can automatically update pinned library versions for you.'
                    ]
                ]
        )
    }
}

def checkOldLibraryVersions(jenkinsLibraryDeprecationConfig) {
    jenkinsLibraryDeprecationConfig.each { configKey, deprecation ->
        def patterns = []
        if (deprecation.pattern instanceof Collection) {
            patterns.addAll(deprecation.pattern)
        } else {
            patterns.addAll(
                deprecation.pattern
                    .toString()
                    .split(/\|/)
                    .collect { it.trim() }
                    .findAll { it }
            )
        }

        patterns.each { pattern ->
            int status = sh(
                script: """
                chmod +x check-library-version.sh
                ./check-library-version.sh 'deprecation' '${pattern}' '${deprecation.version}' '${deprecation.date_deadline}'
                """,
                returnStatus: true
            )

            buildAndAddWarning(
                status,
                    "old_library_version",
                    [
                        ['Your Jenkinsfile references a deprecated Jenkins library version.'],
                        [
                            "Update it to use *Infrastructure@${deprecation.version}*, then check the",
                            'migration guide and rollout tracker before raising a PR. Some repositories',
                            'also need Key Vault or PostgreSQL module changes as part of this migration.'
                        ],
                        [
                            'Migration guide:',
                            'https://tools.hmcts.net/confluence/spaces/DTSPO/pages/1973509936/Jenkins+Library+Migration+Guide'
                        ],
                        [
                            'Rollout tracker:',
                            'https://tools.hmcts.net/confluence/spaces/DTSPO/pages/1973305638/Migration+rollout+tracker'
                        ]
                    ],
                    deprecation.date_deadline
            )
        }
    }
}