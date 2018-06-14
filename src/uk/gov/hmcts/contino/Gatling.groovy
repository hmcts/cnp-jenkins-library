package uk.gov.hmcts.contino

class Gatling implements Serializable {

  public static final String GATLING_REPORTS_PATH  = 'build/gatling/reports'
  public static final String GATLING_REPORTS_DIR   = '$WORKSPACE/' + GATLING_REPORTS_PATH
  public static final String GATLING_BINARIES_DIR  = '$WORKSPACE/build/gatling/binaries'
  public static final String GATLING_SIMS_DIR      = '$WORKSPACE/src/gatling/simulations'
  public static final String GATLING_DATA_DIR      = '$WORKSPACE/src/gatling/data'
  public static final String GATLING_BODIES_DIR    = '$WORKSPACE/src/gatling/bodies'
  public static final String GATLING_CONF_DIR      = '$WORKSPACE/src/gatling/conf'
  public static final String GATLING_IMAGE         = 'hmcts/moj-gatling-image:2.3.1-1.0'
  public static final String COSMOSDB_COLLECTION   = 'dbs/jenkins/colls/performance-metrics'

  // with Gatling command-line you can't specify a configuration directory, so we need to bind-mount it
  // TODO: use host networking for now as 1) DNS seems to be broken in container and 2) may need it for local perf. testing against localhost
  public static final String GATLING_RUN_ARGS     = '--network=host -v ' + GATLING_CONF_DIR + ':/etc/gatling/conf'

  def steps

  Gatling(steps) {
    this.steps = steps
  }

  def execute() {
    this.steps.withDocker(GATLING_IMAGE, GATLING_RUN_ARGS) {
      this.steps.sh "gatling.sh -m -sf ${GATLING_SIMS_DIR} -df ${GATLING_DATA_DIR} -bdf ${GATLING_BODIES_DIR} -bf ${GATLING_BINARIES_DIR} -rf ${GATLING_REPORTS_DIR}"
    }

    // jenkins gatling plugin
    this.steps.gatlingArchive()
  }

}
