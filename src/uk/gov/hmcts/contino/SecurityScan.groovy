package uk.gov.hmcts.contino


class SecurityScan implements Serializable {
    public static final String OWASP_ZAP_IMAGE = 'owasp/zap2docker-stable:2.13.0'
    public static final String OWASP_ZAP_ARGS = '-u 0:0 --name zap --platform=linux/arm64 -p 1001:1001 -v $WORKSPACE:/zap/wrk/:rw'
    public static final String GLUEIMAGE = 'hmctspublic.azurecr.io/zap-glue:c14eff2a-1692784552'
    public static final String GLUE_ARGS = '-u 0:0 --name=Glue -v ${WORKSPACE}:/tmp -w /tmp'
    def steps

    SecurityScan(steps) {
        this.steps = steps
    }

    def execute() {
        try {
            this.steps.sh '''
                docker ps -a
                if docker ps -a | grep zap | grep Exit; then docker rm zap; fi
                docker ps -a
                '''
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
