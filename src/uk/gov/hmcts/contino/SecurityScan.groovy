package uk.gov.hmcts.contino


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
            this.steps.withDocker(OWASP_ZAP_IMAGE, OWASP_ZAP_ARGS) {
                this.steps.sh '''
                    chmod +x security.sh
                    ./security.sh
                    '''
            }
            this.steps.sh '''
                cd /glue
                ./run_glue.sh "$OLDPWD/audit.json" "$OLDPWD/report.json"
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
