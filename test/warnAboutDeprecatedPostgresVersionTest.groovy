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
            [database: [postgresql_flexible_server_sku_v3: [
                version: 'v3',
                date_deadline: '2027-06-30',
                upgrade_guide: 'https://hmcts.atlassian.net/wiki/spaces/DTSPO/pages/340297585/PostgreSQL+Flexible+Server+14+-+18+Upgrade+Validation+SKU+Compute+Upgrade'
            ]]]
        })
        helper.registerAllowedMethod('libraryResource', [String.class], { 'script body' })
        helper.registerAllowedMethod('writeFile', [Map.class], {})
        helper.registerAllowedMethod('sh', [String.class], { String command ->
            shellCommands << command
            0
        })
        helper.registerAllowedMethod('sh', [Map.class], { Map arguments ->
            shellCommands << arguments.script
            arguments.returnStdout ? plannedPostgresResources : 0
        })

        script = loadScript('vars/warnAboutDeprecatedPostgresVersion.groovy')
    }

    @Test
    void 'does not warn for a v5 SKU'() {
        given:
        plannedPostgresResources = 'module.database.azurerm_postgresql_flexible_server.this\tGP_Standard_D2ds_v5'

        when:
        script.call()

        then:
        assertThat(WarningCollector.pipelineWarnings).isEmpty()
    }

    @Test
    void 'warns for a v3 SKU with the SKU deadline and upgrade guide'() {
        given:
        plannedPostgresResources = 'module.database.azurerm_postgresql_flexible_server.this\tGP_Standard_D2s_v3'

        when:
        script.call()

        then:
        assertThat(WarningCollector.pipelineWarnings).hasSize(1)
        assertThat(WarningCollector.pipelineWarnings.first().warningKey).isEqualTo('deprecated_postgresql_flexible_server_sku_v3')
        assertThat(WarningCollector.pipelineWarnings.first().warningMessage)
            .contains('GP_Standard_D2s_v3')
            .contains('30 June 2027')
            .contains('https://hmcts.atlassian.net/wiki/spaces/DTSPO/pages/340297585/PostgreSQL+Flexible+Server+14+-+18+Upgrade+Validation+SKU+Compute+Upgrade')
    }
}
