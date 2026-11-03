import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.assertj.core.api.Assertions.assertThat

class checkLibraryVersionTest {

    @Rule
    public TemporaryFolder workspace = new TemporaryFolder()

    private Map runCheck(List<String> arguments, Map<String, String> files = [:], Map<String, String> environment = [:]) {
        File testWorkspace = workspace.newFolder()
        files.each { name, content ->
            new File(testWorkspace, name).text = content
        }
        new File(testWorkspace, 'warning-banner.txt').text = 'warning banner'

        File script = new File('resources/uk/gov/hmcts/library/check-library-version.sh').absoluteFile
        ProcessBuilder processBuilder = new ProcessBuilder(['bash', script.path] + arguments)
        processBuilder.directory(testWorkspace)
        processBuilder.redirectErrorStream(true)
        processBuilder.environment().putAll(environment)

        Process process = processBuilder.start()
        [exitCode: process.waitFor(), output: process.inputStream.text]
    }

    @Test
    void 'detects unpinned library references in both Jenkinsfile names and quote styles'() {
        when:
        Map result = runCheck(['unpinned'], [
            Jenkinsfile: "@Library(\"Infrastructure\")",
            Jenkinsfile_CNP: "@Library('Infrastructure')"
        ])

        then:
        assertThat(result.exitCode).isEqualTo(1)
        assertThat(result.output).contains('./Jenkinsfile', './Jenkinsfile_CNP')
    }

    @Test
    void 'matches a literal deprecated version rather than wildcard dots'() {
        when:
        Map nonMatchingResult = runCheck(['deprecation', '2.0.0'], [Jenkinsfile: '@Library("Infrastructure@2x0x0")'])
        Map matchingResult = runCheck(['deprecation', '2.0.0'], [Jenkinsfile_CNP: '@Library("Infrastructure@2.0.0")'])

        then:
        assertThat(nonMatchingResult.exitCode).isZero()
        assertThat(matchingResult.exitCode).isEqualTo(1)
    }

}
