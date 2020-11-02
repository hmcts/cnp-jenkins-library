package uk.gov.hmcts.contino

class Gatling implements Serializable {

  public static final String DEFAULT_GATLING_REPORTS_PATH  = 'build/gatling/reports'
  public static final String GATLING_BINARIES_DIR  = '$WORKSPACE/build/gatling/binaries'
  public static final String GATLING_SIMS_DIR      = '$WORKSPACE/src/gatling/simulations'
  public static final String GATLING_RESOURCES_DIR = '$WORKSPACE/src/gatling/resources'
  public static final String GATLING_CONF_DIR      = '$WORKSPACE/src/gatling/conf'
  public static final String GATLING_JAVA_11_IMAGE  = 'hmcts/gatling:3.1.1-java-11-1.0'
  @SuppressWarnings("unused") // used in publishPerformanceReports
  public static final String COSMOSDB_COLLECTION   = 'dbs/jenkins/colls/performance-metrics'

  // with Gatling command-line you can't specify a configuration directory, so we need to bind-mount it
  // TODO: use host networking for now as 1) DNS seems to be broken in container and 2) may need it for local perf. testing against localhost
  public static final String GATLING_RUN_ARGS     = '--network=host -v ' + GATLING_CONF_DIR + ':/etc/gatling/conf'

  def steps

  Gatling(steps) {
    this.steps = steps
    if (this.steps.env == null) {
      this.steps.metaClass.env = [:]
    }
    this.steps.env.GATLING_REPORTS_PATH = DEFAULT_GATLING_REPORTS_PATH
    this.steps.env.GATLING_REPORTS_DIR = '$WORKSPACE/' + DEFAULT_GATLING_REPORTS_PATH
  }

  def execute() {
    def gatlingImage =  GATLING_JAVA_11_IMAGE

    this.steps.withDocker(gatlingImage, GATLING_RUN_ARGS) {
      this.steps.sh """
        export JAVA_HOME=''
        # nullpointer if no description passed with no stdin attached so pass empty string
        echo "" | gatling.sh --simulations-folder ${GATLING_SIMS_DIR} \
           --binaries-folder ${GATLING_BINARIES_DIR} \
           --results-folder ${steps.env.GATLING_REPORTS_DIR} \
           --resources-folder ${GATLING_RESOURCES_DIR}
      """
    }

    // jenkins gatling plugin
    this.steps.gatlingArchive()
  }

}
