package uk.gov.hmcts.contino


class SecurityScan implements Serializable {
    public static final String OWASP_ZAP_IMAGE = 'ghcr.io/zaproxy/zaproxy:20251215-stable@sha256:8e79e827afb9e8bdba390c829eb3062062cdb407570559e2ddebd49130c00a59'
    public static final String OWASP_ZAP_ARGS = '-u 0:0 --name zap -p 1001:1001 -v $WORKSPACE:/zap/wrk/:rw'
    public static final String GLUEIMAGE = 'hmctsprod.azurecr.io/zap-glue:94516277-1767967285'
    public static final String GLUE_ARGS = '-u 0:0 --name=Glue -v ${WORKSPACE}:/tmp -w /tmp'
    def steps
    def subscription

    SecurityScan(steps) {
        this.steps = steps
        this.subscription = this.steps.env.SUBSCRIPTION_NAME
    }

    def execute() {
        try {
            this.steps.withDocker(OWASP_ZAP_IMAGE, OWASP_ZAP_ARGS) {
                this.steps.sh '''
                    chmod +x security.sh
                    ./security.sh
                    '''
            }
            this.steps.sh '''
                wget https://raw.githubusercontent.com/hmcts/zap-glue/master/jq_pattern -O ${WORKSPACE}/jq_pattern
                jq -f ${WORKSPACE}/jq_pattern ${WORKSPACE}/report.json > ${WORKSPACE}/output.json
                '''
            this.steps.withDocker(GLUEIMAGE, GLUE_ARGS) {
                this.steps.sh '''
                    cd /glue
                    ruby /glue/bin/glue -t Dynamic -T /tmp/output.json -f json --finding-file-path /tmp/audit.json --mapping-file /glue/zaproxy_mapping.json -z
                    '''
            }
        } finally {
            steps.archiveArtifacts allowEmptyArchive: true, artifacts: 'functional-output/**.*'
        }
    }
}
