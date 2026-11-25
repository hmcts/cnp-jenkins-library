import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test
import uk.gov.hmcts.pipeline.DeprecationConfig
import uk.gov.hmcts.pipeline.SlackBlockMessage
import uk.gov.hmcts.pipeline.deprecation.WarningCollector

import static org.assertj.core.api.Assertions.assertThat

class warnAboutDeprecatedPostgresSkuTest extends BasePipelineTest {
    def script
    String affectedResources = ''
    List<String> messages = []
    List<String> commands = []
    int jqStatus = 0
    boolean configAvailable = true

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
            [database: configAvailable ? [postgresql_v3_sku: [
                sku: 'v3',
                date_deadline: 'June 2027',
                message: 'v3 reserved instance coverage ends. Upgrade guidance: https://example.com/upgrade'
            ]] : [:]]
        })
        helper.registerAllowedMethod('libraryResource', [String.class], { 'script body' })
        helper.registerAllowedMethod('writeFile', [Map.class], {})
        helper.registerAllowedMethod('echo', [String.class], { String message -> messages << message })
        helper.registerAllowedMethod('sh', [String.class], { String command -> commands << command; 0 })
        helper.registerAllowedMethod('sh', [Map.class], { Map arguments ->
            commands << arguments.script.toString()
            arguments.returnStdout ? affectedResources : jqStatus
        })
        script = loadScript('vars/warnAboutDeprecatedPostgresSku.groovy')
    }

    @Test
    void 'does not add a warning when no v3 resources are found'() {
        script.call()
        assertThat(WarningCollector.pipelineWarnings).isEmpty()
        assertThat(messages).isEmpty()
    }

    @Test
    void 'includes resource SKU deadline and guidance in a non-enforcing warning'() {
        affectedResources = 'module.db.azurerm_postgresql_flexible_server.db\tcurrent\tGP_Standard_D2s_v3'
        script.call()

        assertThat(WarningCollector.pipelineWarnings).hasSize(1)
        def warning = WarningCollector.pipelineWarnings.first()
        assertThat(warning.warningKey).isEqualTo('deprecated_postgresql_v3_sku')
        assertThat(warning.includeDeadlineMessage).isFalse()
        assertThat(warning.warningMessage)
            .contains('module.db.azurerm_postgresql_flexible_server.db', 'GP_Standard_D2s_v3', 'June 2027', 'https://example.com/upgrade')
        assertThat(messages.first()).contains('GP_Standard_D2s_v3', 'June 2027')
        assertThat(commands).contains('rm -f check-deprecated-postgres-sku.sh')
        assertThat(commands).contains("./check-deprecated-postgres-sku.sh tfplan 'v3'")
    }

    @Test
    void 'reports missing configuration without failing the pipeline'() {
        configAvailable = false
        script.call()
        assertThat(messages).anyMatch { it.contains('configuration') && it.contains('could not run') }
        assertThat(WarningCollector.pipelineWarnings).isEmpty()
    }

    @Test
    void 'reports missing jq without failing the pipeline'() {
        jqStatus = 1
        script.call()
        assertThat(messages).anyMatch { it.contains('jq') && it.contains('could not run') }
        assertThat(WarningCollector.pipelineWarnings).isEmpty()
    }
}
