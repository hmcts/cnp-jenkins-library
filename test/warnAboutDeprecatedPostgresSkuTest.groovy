import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test
import uk.gov.hmcts.pipeline.DeprecationConfig
import uk.gov.hmcts.pipeline.SlackBlockMessage
import uk.gov.hmcts.pipeline.deprecation.WarningCollector

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy

class warnAboutDeprecatedPostgresSkuTest extends BasePipelineTest {
    def script
    String affectedResources = ''
    List<String> messages = []
    List<String> commands = []
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
                message: 'v3 reserved instance coverage ends in June 2027. Upgrade guidance: https://example.com/upgrade'
            ]] : [:]]
        })
        helper.registerAllowedMethod('libraryResource', [String.class], { 'script body' })
        helper.registerAllowedMethod('writeFile', [Map.class], {})
        helper.registerAllowedMethod('echo', [String.class], { String message -> messages << message })
        helper.registerAllowedMethod('sh', [String.class], { String command -> commands << command; 0 })
        helper.registerAllowedMethod('sh', [Map.class], { Map arguments ->
            commands << arguments.script.toString()
            affectedResources
        })
        script = loadScript('vars/warnAboutDeprecatedPostgresSku.groovy')
    }

    @Test
    void 'does not add a warning when no v3 resources are found'() {
        script.call(binding.env.GIT_URL, 'stg')
        assertThat(WarningCollector.pipelineWarnings).isEmpty()
        assertThat(messages).isEmpty()
    }

    @Test
    void 'includes resource SKU deadline and guidance in a non-enforcing warning'() {
        affectedResources = 'module.db.azurerm_postgresql_flexible_server.db\tGP_Standard_D2s_v3'
        script.call(binding.env.GIT_URL, 'stg')

        assertThat(WarningCollector.pipelineWarnings).hasSize(1)
        def warning = WarningCollector.pipelineWarnings.first()
        assertThat(warning.warningKey).isEqualTo('deprecated_postgresql_v3_sku')
        assertThat(warning.deprecationDate).isNull()
        assertThat(warning.warningMessage)
            .contains('module.db.azurerm_postgresql_flexible_server.db', 'GP_Standard_D2s_v3', 'June 2027', 'https://example.com/upgrade')
        assertThat(messages.first()).contains('GP_Standard_D2s_v3', 'June 2027')
        assertThat(WarningCollector.getSlackWarningMessage().blocks.first().text.text)
            .contains('Environment: stg', 'Affected Terraform resources (address and SKU):', 'GP_Standard_D2s_v3', 'June 2027')
        assertThat(commands).contains('rm -f check-deprecated-postgres-sku.sh')
        assertThat(commands).contains("./check-deprecated-postgres-sku.sh tfplan 'v3'")
    }

    @Test
    void 'fails explicitly when the check is not configured'() {
        configAvailable = false
        assertThatThrownBy { script.call(binding.env.GIT_URL, 'stg') }
            .isInstanceOf(IllegalStateException)
            .hasMessageContaining('Missing database.postgresql_v3_sku')
        assertThat(WarningCollector.pipelineWarnings).isEmpty()
    }

    @Test
    void 'labels warnings from separate environments without repeating the renewal date'() {
        affectedResources = 'module.db.azurerm_postgresql_flexible_server.db\tGP_Standard_D2s_v3'
        script.call(binding.env.GIT_URL, 'stg')
        script.call(binding.env.GIT_URL, 'prod')

        def warnings = WarningCollector.getSlackWarningMessage().blocks.collect { it.text.text }
        assertThat(warnings).hasSize(2)
        assertThat(warnings[0]).contains('Environment: stg').doesNotContain('Environment: prod')
        assertThat(warnings[1]).contains('Environment: prod').doesNotContain('Environment: stg')
        warnings.each { warning ->
            assertThat(warning.count('June 2027')).isEqualTo(1)
        }
    }
}
