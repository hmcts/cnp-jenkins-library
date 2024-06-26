package uk.gov.hmcts.contino


class SecurityScan implements Serializable {
    public static final String OWASP_ZAP_IMAGE = 'ghcr.io/zaproxy/zaproxy:20240402-stable@sha256:3280adc730131f1f4460ab226b0f85e3e9ab3301ef5a7030f745ac4dd6b6ff87'
    public static final String OWASP_ZAP_ARGS = '-u 0:0 --name zap -p 1001:1001 -v $WORKSPACE:/zap/wrk/:rw'
    public static final String GLUEIMAGE = 'hmctspublic.azurecr.io/zap-glue:c14eff2a-1692784552'
    public static final String GLUE_ARGS = '-u 0:0 --name=Glue -v ${WORKSPACE}:/tmp -w /tmp'
    def steps

    SecurityScan(steps) {
        this.steps = steps
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
