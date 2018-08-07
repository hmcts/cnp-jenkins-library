#!groovy

/**
 * Run a closure sauce connect proxy
 *
 * @param sauceid credentialid for accessing sauce tunnel
 * @param body the closure
 */
def call(String sauceid, Closure body) {
    sauce(sauceid) {
        sauceconnect(options: '--verbose  --tunnel-identifier reformtunnel', useGeneratedTunnelIdentifier: false,
                useLatestSauceConnect: true, verboseLogging: true) {
            body().call()
        }
      saucePublisher()

    }
}
