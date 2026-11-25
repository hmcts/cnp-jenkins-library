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

    @Test
    void 'semantic version cutoff deprecates versions at or below it'() {
        when:
        Map<String, Integer> expectedExitCodes = [
            '1.99.99': 1,
            '2.3.99': 1,
            '2.4.0': 1,
            '2.4.1': 1,
            '2.4.2': 0,
            '2.5.0': 0,
            '3.0.0': 0
        ]

        then:
        expectedExitCodes.each { version, expectedExitCode ->
            Map result = runCheck(
                ['deprecation', '2.4.1'],
                [Jenkinsfile: "@Library(\"Infrastructure@${version}\")"]
            )

            assertThat(result.exitCode)
                .as("Infrastructure@${version}")
                .isEqualTo(expectedExitCode)
        }
    }

    @Test
    void 'semantic version cutoff checks versioned tags in supported Jenkinsfiles and quote styles'() {
        when:
        Map result = runCheck(['deprecation', '2.4.1'], [
            Jenkinsfile: "@Library('Infrastructure@2.4.2')",
            Jenkinsfile_CNP: '@Library("Infrastructure@2.4.0")',
            Jenkinsfile_nightly: '@Library( Infrastructure@2.3.0 )'
        ])

        then:
        assertThat(result.exitCode).isEqualTo(1)
        assertThat(result.output).contains('./Jenkinsfile_CNP', './Jenkinsfile_nightly')
        assertThat(result.output).doesNotContain('  - ./Jenkinsfile\n')
    }

    @Test
    void 'non-semantic deprecated patterns remain exact matches'() {
        when:
        Map nonMatchingResult = runCheck(
            ['deprecation', 'release-2.4'],
            [Jenkinsfile: '@Library("Infrastructure@release-2x4")']
        )
        Map matchingResult = runCheck(
            ['deprecation', 'release-2.4'],
            [Jenkinsfile: '@Library("Infrastructure@release-2.4")']
        )

        then:
        assertThat(nonMatchingResult.exitCode).isZero()
        assertThat(matchingResult.exitCode).isEqualTo(1)
    }

    @Test
    void 'semantic version cutoff checks the versioned tag loaded by Jenkins'() {
        when:
        Map deprecatedResult = runCheck(['deprecation', '2.4.1', '3.0.0', '2027-01-01', '2.3.9'])
        Map supportedResult = runCheck(['deprecation', '2.4.1', '3.0.0', '2027-01-01', '2.4.2'])

        then:
        assertThat(deprecatedResult.exitCode).isEqualTo(1)
        assertThat(deprecatedResult.output).contains('Loaded Infrastructure library version: 2.3.9')
        assertThat(supportedResult.exitCode).isZero()
    }

    @Test
    void 'deprecated version recommends the latest release when no upgrade version is supplied'() {
        when:
        Map result = runCheck(['deprecation', '2.4.1'], [
            Jenkinsfile: '@Library("Infrastructure@2.4.0")'
        ])

        then:
        assertThat(result.exitCode).isEqualTo(1)
        assertThat(result.output)
            .contains('Check for the latest release at https://github.com/hmcts/cnp-jenkins-library/releases')
            .doesNotContain('Infrastructure@)')
    }

}
