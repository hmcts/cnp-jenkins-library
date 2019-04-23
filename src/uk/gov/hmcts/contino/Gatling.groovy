package uk.gov.hmcts.contino

class Gatling implements Serializable {

  public static final String GATLING_REPORTS_PATH  = 'build/gatling/reports'
  public static final String GATLING_REPORTS_DIR   = '$WORKSPACE/' + GATLING_REPORTS_PATH
  public static final String GATLING_BINARIES_DIR  = '$WORKSPACE/build/gatling/binaries'
  public static final String GATLING_SIMS_DIR      = '$WORKSPACE/src/gatling/simulations'
  public static final String GATLING_DATA_DIR      = '$WORKSPACE/src/gatling/data'
  public static final String GATLING_BODIES_DIR    = '$WORKSPACE/src/gatling/bodies'
  public static final String GATLING_CONF_DIR      = '$WORKSPACE/src/gatling/conf'
  public static final String GATLING_JAVA_8_IMAGE  = 'hmcts/gatling:3.1.1-java-8-1.0'
  public static final String GATLING_JAVA_11_IMAGE  = 'hmcts/gatling:3.1.1-java-11-1.0'
  @SuppressWarnings("unused") // used in publishPerformanceReports
  public static final String COSMOSDB_COLLECTION   = 'dbs/jenkins/colls/performance-metrics'

  // with Gatling command-line you can't specify a configuration directory, so we need to bind-mount it
  // TODO: use host networking for now as 1) DNS seems to be broken in container and 2) may need it for local perf. testing against localhost
  public static final String GATLING_RUN_ARGS     = '--network=host -v ' + GATLING_CONF_DIR + ':/etc/gatling/conf'

  def steps

  Gatling(steps) {
    this.steps = steps
  }

  def execute() {
    def gatlingImage = steps.env?.JAVA_MAJOR_VERSION == "11" ? GATLING_JAVA_11_IMAGE : GATLING_JAVA_8_IMAGE

    this.steps.withDocker(gatlingImage, GATLING_RUN_ARGS) {
      this.steps.sh """
        export JAVA_HOME=''
        gatling.sh -m --simulations-folder ${GATLING_SIMS_DIR} \
           --binaries-folder ${GATLING_BINARIES_DIR} \
           --results-folder ${GATLING_REPORTS_DIR}
      """
    }

    """

Usage: gatling [options]

  -h, --help               Show help (this message) and exit
  -nr, --no-reports        Runs simulation but does not generate reports
  -ro, --reports-only <directoryName>
                           Generates the reports for the simulation in <directoryName>
  -rsf, --resources-folder <directoryPath>
                           Uses <directoryPath> as the absolute path of the directory where resources are stored
  -rf, --results-folder <directoryPath>
                           Uses <directoryPath> as the absolute path of the directory where results are stored
  -sf, --simulations-folder <directoryPath>
                           Uses <directoryPath> to discover simulations that could be run
  -bf, --binaries-folder <directoryPath>
                           Uses <directoryPath> as the absolute path of the directory where Gatling should produce compiled binaries
  -s, --simulation <className>
                           Runs <className> simulation
  -rd, --run-description <description>
                           A short <description> of the run to include in the report
"""

    // jenkins gatling plugin
    this.steps.gatlingArchive()
  }

}
