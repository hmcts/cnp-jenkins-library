package uk.gov.hmcts.contino

import uk.gov.hmcts.pipeline.deprecation.WarningCollector
import java.time.LocalDate

class SecurityScan implements Serializable {
    public static final String OWASP_ZAP_IMAGE = 'owasp/zap2docker-stable:2.11.1'
    public static final String OWASP_ZAP_ARGS = '-u 0:0 --name zap -p 1001:1001 -v $WORKSPACE:/zap/wrk/:rw'
    public static final String GLUEIMAGE = 'hmcts/zap-glue:latest'
    public static final String GLUE_ARGS = '-u 0:0 --name=Glue -w $(pwd):/tmp'
    def steps

    SecurityScan(steps) {
        this.steps = steps
    }

    def execute() {
        try {
            WarningCollector.addPipelineWarning("security_scan_moved", "Please remove security.sh from repository and use enableSecurityScanFrontend() or enableSecurityScanBackend() instead of enabledSecurityScan(), LocalDate.of(2023, 03, 27))

            this.steps.withDocker(OWASP_ZAP_IMAGE, OWASP_ZAP_ARGS) {
                this.steps.sh '''
                    chmod +x security.sh
                    ./security.sh
                    '''
            }
            this.steps.withDocker(GLUEIMAGE, GLUE_ARGS) {
                this.steps.sh '''
                    cd /glue
                    ./run_glue.sh "$OLDPWD/audit.json" "$OLDPWD/report.json"
                    '''
            }
        } finally {
            steps.archiveArtifacts allowEmptyArchive: true, artifacts: 'functional-output/**.*'
        }
    }
}
