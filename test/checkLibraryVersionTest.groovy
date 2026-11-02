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
    void 'detects an allowed branch library reference'() {
        when:
        Map result = runCheck(['branch', 'feature/test'], [Jenkinsfile: "@Library('Infrastructure@feature/test')"])

        then:
        assertThat(result.exitCode).isEqualTo(1)
        assertThat(result.output).contains('Allowed Infrastructure library branch in use!')
    }

    @Test
    void 'detects allowed branch names containing every Git-valid extended regex metacharacter'() {
        given:
        List<String> branchNames = [
            'feature.dot',
            'feature$cash',
            'feature+plus',
            'feature(paren)',
            'feature]bracket',
            'feature{brace',
            'feature}brace',
            'feature|pipe'
        ]

        expect:
        branchNames.each { branchName ->
            Map result = runCheck(['branch', branchName], [Jenkinsfile: "@Library(\"Infrastructure@${branchName}\")"])

            assertThat(result.exitCode)
                .describedAs("Expected branch '${branchName}' to be detected literally")
                .isEqualTo(1)
        }
    }

    @Test
    void 'does not treat a dot in an allowed branch name as a wildcard'() {
        when:
        Map result = runCheck(['branch', 'feature.v1'], [Jenkinsfile: '@Library("Infrastructure@featureXv1")'])

        then:
        assertThat(result.exitCode).isZero()
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