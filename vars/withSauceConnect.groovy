#!groovy

/**
 * Run a closure sauce connect proxy
 *
 * @param sauceid credentialid for accessing sauce tunnel
 * @param body the closure
 */
def call(String sauceid, Closure body) {
    sauce(sauceid) {
            sauceconnect(options: '--shared-tunnel --verbose --tunnel-pool --tunnel-name reformtunnel', useGeneratedTunnelIdentifier: false, verboseLogging: true) {
            body()
        }
    }
}
