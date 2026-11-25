import groovy.json.JsonOutput
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.assertj.core.api.Assertions.assertThat

class checkDeprecatedPostgresSkuTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder()

    private Map runCheck(List changes) {
        File directory = temporaryFolder.newFolder()
        File plan = new File(directory, 'plan.json')
        plan.text = JsonOutput.toJson(changes == null ? [:] : [resource_changes: changes])
        File terraform = new File(directory, 'terraform')
        terraform.text = '#!/bin/sh\ncat "$3"\n'
        assert terraform.setExecutable(true)
        def processBuilder = new ProcessBuilder(
            'bash', 'resources/uk/gov/hmcts/infrastructure/check-deprecated-postgres-sku.sh', plan.absolutePath
        )
        processBuilder.environment().put('PATH', directory.absolutePath + ':' + System.getenv('PATH'))
        Process process = processBuilder.start()
        String stdout = process.inputStream.text
        String stderr = process.errorStream.text
        int status = process.waitFor()
        [status: status, stdout: stdout, stderr: stderr]
    }

    private Map server(String address, String before, String after, boolean unknown = false) {
        [
            address: address,
            mode: 'managed',
            type: 'azurerm_postgresql_flexible_server',
            change: [
                before: before == null ? null : [sku_name: before],
                after: [sku_name: after],
                after_unknown: [sku_name: unknown]
            ]
        ]
    }

    @Test
    void 'finds current and planned v3 SKUs but excludes v5 and other resources'() {
        def result = runCheck([
            server('module.migration.server', 'GP_Standard_D2s_v3', 'GP_Standard_D2ds_v5'),
            server('module.new.server', null, 'GP_Standard_D4s_v3'),
            server('module.v5.server', 'GP_Standard_D2ds_v5', 'GP_Standard_D2ds_v5'),
            [address: 'other', mode: 'managed', type: 'other', change: [after: [sku_name: 'GP_Standard_D2s_v3']]]
        ])
        assertThat(result.status).isEqualTo(0)
        assertThat(result.stdout).contains(
            'module.migration.server\tcurrent\tGP_Standard_D2s_v3',
            'module.new.server\tplanned\tGP_Standard_D4s_v3'
        ).doesNotContain('module.v5.server', 'other')
    }

    @Test
    void 'reports unknown planned SKU without claiming it is v3'() {
        def result = runCheck([server('module.unknown.server', null, null, true)])
        assertThat(result.status).isEqualTo(0)
        assertThat(result.stdout).isEmpty()
        assertThat(result.stderr).contains('unknown', 'module.unknown.server')
    }

    @Test
    void 'does not warn for an empty plan'() {
        def result = runCheck(null)
        assertThat(result.status).isEqualTo(0)
        assertThat(result.stdout).isEmpty()
    }
}
