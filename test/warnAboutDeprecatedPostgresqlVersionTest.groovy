import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test
import uk.gov.hmcts.pipeline.DeprecationConfig
import uk.gov.hmcts.pipeline.SlackBlockMessage
import uk.gov.hmcts.pipeline.deprecation.WarningCollector

import java.time.LocalDate

import static org.assertj.core.api.Assertions.assertThat

class warnAboutDeprecatedPostgresqlVersionTest extends BasePipelineTest {

    def script
    String detectedVersions = ''
    List<String> shellCommands = []

    @Override
    @Before
    void setUp() {
        super.setUp()

        WarningCollector.pipelineWarnings.clear()
        WarningCollector.slackMessage = new SlackBlockMessage()
        DeprecationConfig.deprecationConfigInternal = null

        binding.setVariable('env', [GIT_URL: 'https://github.com/hmcts/example-service.git'])
        helper.registerAllowedMethod('httpRequest', [Map.class], { [content: 'deprecations'] })
        helper.registerAllowedMethod('readYaml', [Map.class], { Map arguments ->
            [
                postgresql_server_version: [
                    version: '15',
                    date_deadline: LocalDate.now().plusDays(10).toString(),
                    message: 'PostgreSQL 14 and below are deprecated. Please upgrade to PostgreSQL 15 or later.'
                ]
            ]
        })
        helper.registerAllowedMethod('libraryResource', [String.class], { 'resource content' })
        helper.registerAllowedMethod('writeFile', [Map.class], {})
        helper.registerAllowedMethod('sh', [String.class], { String command ->
            shellCommands << command
        })
        helper.registerAllowedMethod('sh', [Map.class], { Map arguments ->
            shellCommands << arguments.script
            detectedVersions
        })

        script = loadScript('vars/warnAboutDeprecatedPostgresqlVersion.groovy')
    }

    @Test
    void 'adds a warning when the plan contains PostgreSQL below the configured version'() {
        given:
        detectedVersions = 'PostgreSQL 14 is below the required major version 15.'

        when:
        script.call()

        then:
        assertThat(WarningCollector.pipelineWarnings*.warningKey)
            .containsExactly('deprecated_postgresql_server_version')
        assertThat(WarningCollector.pipelineWarnings.first().warningMessage)
            .contains('PostgreSQL 14 and below')
        assertThat(shellCommands).contains("./check-deprecated-postgresql-version.sh '15' tfplan")
    }

    @Test
    void 'does not add a warning when no unsupported PostgreSQL version is in the plan'() {
        when:
        script.call()

        then:
        assertThat(WarningCollector.pipelineWarnings).isEmpty()
    }
}
