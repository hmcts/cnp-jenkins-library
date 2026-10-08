import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test
import uk.gov.hmcts.pipeline.DeprecationConfig
import uk.gov.hmcts.pipeline.SlackBlockMessage
import uk.gov.hmcts.pipeline.deprecation.WarningCollector

import static org.assertj.core.api.Assertions.assertThat

class warnAboutDeprecatedPostgresVersionTest extends BasePipelineTest {

    def script
    String plannedPostgresResources = ''
    String deprecationDeadline = '2026-11-12'

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
                        version: '15',
                        date_deadline: deprecationDeadline,
                        message: 'PostgreSQL 14 and below are deprecated. Please upgrade to PostgreSQL 15 or later.'
                    ]
                ]
            ]
        })
        helper.registerAllowedMethod('libraryResource', [String.class], { 'script body' })
        helper.registerAllowedMethod('writeFile', [Map.class], {})
        helper.registerAllowedMethod('sh', [String.class], { 0 })
        helper.registerAllowedMethod('sh', [Map.class], { Map arguments ->
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
    void 'warns with affected resource details when the plan includes PostgreSQL 14 or below'() {
        given:
        plannedPostgresResources = 'module.database.azurerm_postgresql_flexible_server.this\t14'

        when:
        script.call()

        then:
        assertThat(WarningCollector.pipelineWarnings).hasSize(1)
        assertThat(WarningCollector.pipelineWarnings.first().warningKey).isEqualTo('deprecated_postgresql_version')
        assertThat(WarningCollector.pipelineWarnings.first().warningMessage)
            .contains('PostgreSQL 14 and below are deprecated')
            .contains('module.database.azurerm_postgresql_flexible_server.this')
            .contains('14')
    }

    @Test(expected = RuntimeException.class)
    void 'fails once the deprecation deadline has passed for an affected plan'() {
        given:
        plannedPostgresResources = 'module.database.azurerm_postgresql_flexible_server.this\t14'
        deprecationDeadline = '2020-01-01'

        when:
        script.call()

    }
}
