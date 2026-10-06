import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test
import uk.gov.hmcts.pipeline.DeprecationConfig
import uk.gov.hmcts.pipeline.LibraryBranchControls
import uk.gov.hmcts.pipeline.SlackBlockMessage
import uk.gov.hmcts.pipeline.deprecation.WarningCollector

import java.time.LocalDate

import static org.assertj.core.api.Assertions.assertThat
import static org.junit.Assert.fail

class warnAboutCustomLibraryVersionTest extends BasePipelineTest {

    def script
    Map<String, Integer> statuses = [unpinned: 0, branch: 0, deprecation: 0]
    List<Map> libraryBranches = [[name: 'master', allowed: true]]
    def deprecationPatterns = '1.0.0'
    String deprecationDeadline = LocalDate.now().minusDays(1).toString()
    List<String> shellCommands = []

    @Override
    @Before
    void setUp() {
        super.setUp()

        WarningCollector.pipelineWarnings.clear()
        WarningCollector.slackMessage = new SlackBlockMessage()
        DeprecationConfig.deprecationConfigInternal = null
        LibraryBranchControls.libraryBranchControls = null

        binding.setVariable('env', [GIT_CREDENTIALS_ID: 'credentials'])
        helper.registerAllowedMethod('libraryResource', [String.class], { 'resource content' })
        helper.registerAllowedMethod('writeFile', [Map.class], {})
        helper.registerAllowedMethod('httpRequest', [Map.class], { Map request ->
            [content: request.url.contains('allowed-library-branches') ? 'branches' : 'deprecations']
        })
        helper.registerAllowedMethod('readYaml', [Map.class], { Map arguments ->
            if (arguments.text == 'branches') {
                return [branches: libraryBranches]
            }

            return [
                jenkins: [
                    legacyVersion: [
                        pattern: deprecationPatterns,
                        version: '2.0.0',
                        date_deadline: deprecationDeadline
                    ]
                ]
            ]
        })
        helper.registerAllowedMethod('sh', [Map.class], { Map arguments ->
            String command = arguments.script
            shellCommands << command

            if (command.contains("'unpinned'")) {
                return statuses.unpinned
            }
            if (command.contains("'branch'")) {
                return statuses.branch
            }
            if (command.contains("'deprecation'")) {
                return statuses.deprecation
            }

            return 0
        })
        helper.registerAllowedMethod('sh', [String.class], { String command ->
            shellCommands << command
            return 0
        })

        script = loadScript('vars/warnAboutCustomLibraryVersion.groovy')
    }

    @Test
    void 'buildAndAddWarning does nothing when no custom version is detected'() {
        when:
        script.buildAndAddWarning(0, 'warning_key', [['A warning']])

        then:
        assertThat(WarningCollector.pipelineWarnings).isEmpty()
    }

    @Test
    void 'buildAndAddWarning adds the expected warning when a custom version is detected'() {
        when:
        script.buildAndAddWarning(1, 'warning_key', [['First paragraph'], ['Second paragraph']])

        then:
        assertThat(WarningCollector.pipelineWarnings).hasSize(1)
        assertThat(WarningCollector.pipelineWarnings.first().warningKey).isEqualTo('warning_key')
        assertThat(WarningCollector.pipelineWarnings.first().warningMessage)
            .isEqualTo('First paragraph\n\nSecond paragraph')
    }

    @Test
    void 'custom version check runs for nightly jobs'() {
        given:
        binding.getVariable('env').JOB_NAME = 'SERVICE-NIGHTLY'
        statuses.unpinned = 1

        when:
        script.call()

        then:
        assertThat(shellCommands.findAll { it.contains("'unpinned'") }).hasSize(1)
        assertThat(WarningCollector.pipelineWarnings*.warningKey)
            .containsExactly('unpinned_infrastructure_library')
    }

    @Test
    void 'custom version check skips all helper scripts for SBOX and sandbox subscriptions'() {
        expect:
        ['SBOX-TEST', 'sandbox-test'].each { subscriptionName ->
            binding.getVariable('env').JENKINS_SUBSCRIPTION_NAME = subscriptionName

            script.call()

            assertThat(shellCommands).isEmpty()
            assertThat(WarningCollector.pipelineWarnings).isEmpty()
        }
    }

    @Test
    void 'custom version check adds only the unpinned warning when an unpinned library is detected'() {
        given:
        statuses.unpinned = 1

        when:
        script.call()

        then:
        assertThat(WarningCollector.pipelineWarnings*.warningKey)
            .containsExactly('unpinned_infrastructure_library')
        assertThat(WarningCollector.pipelineWarnings.first().warningMessage)
            .contains('@Library("Infrastructure")')
    }

    @Test
    void 'custom version check adds only the allowed branch warning when an allowed branch is detected'() {
        given:
        libraryBranches = [[name: 'master', allowed: true], [name: 'feature/test', allowed: true]]
        statuses.branch = 1

        when:
        script.call()

        then:
        assertThat(WarningCollector.pipelineWarnings*.warningKey)
            .containsExactly('allowed_infrastructure_library_branch')
        assertThat(WarningCollector.pipelineWarnings.first().warningMessage).contains('*feature/test*.')
    }

    @Test
    void 'custom version check does not check master as an allowed branch version'() {
        when:
        script.call()

        then:
        assertThat(shellCommands.findAll { it.contains("'branch'") }).isEmpty()
        assertThat(WarningCollector.pipelineWarnings).isEmpty()
    }

    @Test
    void 'custom version check adds an old library warning and fails when an expired version is detected'() {
        given:
        statuses.deprecation = 1

        when:
        try {
            script.call()
            fail('Expected an expired deprecated library version to fail the pipeline')
        } catch (RuntimeException expected) {
            assertThat(expected.message).contains('Your Jenkinsfile references a deprecated Jenkins library version.')
        }

        then:
        assertThat(WarningCollector.pipelineWarnings*.warningKey).containsExactly('old_library_version')
        assertThat(shellCommands).contains('rm -f check-library-version.sh', 'rm -f warning-banner.txt')
    }

    @Test
    void 'custom version check adds an old library warning without failing before its deadline'() {
        given:
        statuses.deprecation = 1
        deprecationDeadline = LocalDate.now().plusDays(1).toString()

        when:
        script.call()

        then:
        assertThat(WarningCollector.pipelineWarnings*.warningKey).containsExactly('old_library_version')
        assertThat(WarningCollector.pipelineWarnings.first().deprecationDate)
            .isEqualTo(LocalDate.parse(deprecationDeadline))
    }

    @Test
    void 'custom version check adds a warning for each allowed branch detected'() {
        given:
        libraryBranches = [
            [name: 'master', allowed: true],
            [name: 'feature/one', allowed: true],
            [name: 'feature/two', allowed: true]
        ]
        statuses.branch = 1

        when:
        script.call()

        then:
        assertThat(WarningCollector.pipelineWarnings*.warningKey).containsExactly(
            'allowed_infrastructure_library_branch',
            'allowed_infrastructure_library_branch'
        )
        assertThat(WarningCollector.pipelineWarnings[0].warningMessage).contains('*feature/one*.')
        assertThat(WarningCollector.pipelineWarnings[1].warningMessage).contains('*feature/two*.')
    }

    @Test
    void 'custom version check adds a warning for each deprecated pattern detected before its deadline'() {
        given:
        deprecationPatterns = ['1.0.0', '1.1.0']
        deprecationDeadline = LocalDate.now().plusDays(1).toString()
        statuses.deprecation = 1

        when:
        script.call()

        then:
        assertThat(WarningCollector.pipelineWarnings*.warningKey).containsExactly('old_library_version', 'old_library_version')
        assertThat(shellCommands.findAll { it.contains("'deprecation'") }).hasSize(2)
    }

    @Test
    void 'custom version check adds advisory warnings then fails when all custom library versions are detected'() {
        given:
        libraryBranches = [[name: 'master', allowed: true], [name: 'feature/test', allowed: true]]
        statuses.putAll([unpinned: 1, branch: 1, deprecation: 1])

        when:
        try {
            script.call()
            fail('Expected an expired deprecated library version to fail the pipeline')
        } catch (RuntimeException expected) {
            assertThat(expected.message).contains('Your Jenkinsfile references a deprecated Jenkins library version.')
        }

        then:
        assertThat(WarningCollector.pipelineWarnings*.warningKey).containsExactly(
            'unpinned_infrastructure_library',
            'allowed_infrastructure_library_branch',
            'old_library_version'
        )
        assertThat(WarningCollector.pipelineWarnings*.warningMessage).containsExactly(
            'Your Jenkinsfile is tracking the default branch for Infrastructure via @Library("Infrastructure").\n\n' +
                "This means the pipeline is following the repository's default branch (master), not a fixed library version, " +
                'so it can change unexpectedly when upstream library updates are merged. Check for the latest release at ' +
                'https://github.com/hmcts/cnp-jenkins-library/releases, then update to a fixed library version to keep the ' +
                'pipeline stable. Renovate can automatically update pinned library versions for you.',
            'Your Jenkinsfile is using an allowed Infrastructure library branch *feature/test*.\n\n' +
                'Merge the changes you need from this branch, check for the latest release at ' +
                'https://github.com/hmcts/cnp-jenkins-library/releases, then switch to a fixed library version. Pinned versions ' +
                'receive the latest library features and reduce the chance of upstream changes unexpectedly breaking your pipeline. ' +
                'Renovate can automatically update pinned library versions for you.',
            'Your Jenkinsfile references a deprecated Jenkins library version.\n\n' +
                'Update it to use *Infrastructure@2.0.0*'
        )
    }

    @Test
    void 'custom version check adds no warnings when no deprecated versions are detected'() {
        when:
        script.call()

        then:
        assertThat(WarningCollector.pipelineWarnings).isEmpty()
    }
}