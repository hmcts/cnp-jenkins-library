import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test
import uk.gov.hmcts.pipeline.DeprecationConfig
import uk.gov.hmcts.pipeline.SlackBlockMessage
import uk.gov.hmcts.pipeline.deprecation.WarningCollector

import static org.assertj.core.api.Assertions.assertThat
import static org.junit.Assert.fail

class warnAboutDeprecatedPostgresVersionTest extends BasePipelineTest {

    def script
    String plannedPostgresResources = ''
    String deprecationDeadline = '2026-11-12'
    String deprecationMessage = 'PostgreSQL 15 and below are deprecated. Please upgrade to PostgreSQL 16 or later.'
    List<String> shellCommands = []

    @Override
    @Before
    void setUp() {
        super.setUp()

        WarningCollector.pipelineWarnings.clear()
        WarningCollector.slackMessage = new SlackBlockMessage()
        DeprecationConfig.deprecationConfigInternal = null

        binding.setVariable('env', [GIT_URL: 'https://github.com/hmcts/test-service.git'])
        helper.registerAllowedMethod('httpRequest', [Map.class], { [content: 'deprecations'] })
        helper.registerAllowedMethod('readYaml', [Map.class], {
            [
                database: [
                    postgresql: [
                        version: '16',
                        date_deadline: deprecationDeadline,
                        message: deprecationMessage
                    ]
                ]
            ]
        })
        helper.registerAllowedMethod('libraryResource', [String.class], { 'script body' })
        helper.registerAllowedMethod('writeFile', [Map.class], {})
        helper.registerAllowedMethod('sh', [String.class], { String command ->
            shellCommands << command
            0
        })
        helper.registerAllowedMethod('sh', [Map.class], { Map arguments ->
            shellCommands << arguments.script
            if (arguments.returnStdout) {
                return plannedPostgresResources
            }
            return 0
        })

        script = loadScript('vars/warnAboutDeprecatedPostgresVersion.groovy')
    }

    @Test
    void 'does not warn when the plan has no PostgreSQL versions below the supported version'() {
        when:
        script.call()

        then:
        assertThat(WarningCollector.pipelineWarnings).isEmpty()
    }

    @Test
    void 'warns with affected resource details when the plan includes PostgreSQL 15 or below'() {
        given:
        plannedPostgresResources = 'module.database.azurerm_postgresql_flexible_server.this\t15'

        when:
        script.call()

        then:
        assertThat(WarningCollector.pipelineWarnings).hasSize(1)
        assertThat(WarningCollector.pipelineWarnings.first().warningKey).isEqualTo('deprecated_postgresql_version')
        assertThat(WarningCollector.pipelineWarnings.first().warningMessage)
            .contains('PostgreSQL 15 and below are deprecated')
            .contains('module.database.azurerm_postgresql_flexible_server.this')
            .contains('15')
    }

    @Test
    void 'fails once the deprecation deadline has passed for an affected plan'() {
        given:
        plannedPostgresResources = 'module.database.azurerm_postgresql_flexible_server.this\t14'
        deprecationDeadline = '2020-01-01'

        when:
        try {
          script.call()
          fail('Expected an expired PostgreSQL version to fail the pipeline')
        } catch (RuntimeException expected) {
          assertThat(expected.message).contains('PostgreSQL 15 and below are deprecated')
        }

        then:
        assertThat(WarningCollector.pipelineWarnings*.warningKey)
          .containsExactly('deprecated_postgresql_version')
        assertThat(shellCommands).contains('rm -f check-deprecated-postgres-version.sh')
    }
}
