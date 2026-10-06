#!groovy

def call(config, Closure body) {
  if (!config.buildCache) {
    body()
    return
  }

  def caches = []
  boolean cypressProject = false

  if (fileExists('yarn.lock')) {
    caches.addAll([
      arbitraryFileCache(
        path: 'node_modules',
        cacheName: 'yarn-node-modules',
        cacheValidityDecidingFile: 'yarn.lock,package.json,.yarnrc.yml',
        compressionMethod: 'TARGZ_BEST_SPEED'
      ),
      arbitraryFileCache(
        path: '.',
        includes: '.pnp.cjs,.pnp.loader.mjs,.yarn/install-state.gz',
        cacheName: 'yarn-pnp',
        cacheValidityDecidingFile: 'yarn.lock,package.json,.yarnrc.yml',
        compressionMethod: 'TARGZ_BEST_SPEED'
      )
    ])

    cypressProject = usesCypress()
    if (cypressProject) {
      caches.add(
        arbitraryFileCache(
          path: "${env.HOME}/.cache/Cypress".toString(),
          cacheName: 'cypress-binary',
          cacheValidityDecidingFile: 'yarn.lock,package.json,.yarnrc.yml',
          compressionMethod: 'TARGZ_BEST_SPEED'
        )
      )
    }
  }

  if (fileExists('gradlew')) {
    env.GRADLE_USER_HOME = "${env.WORKSPACE}/.gradle-user-home"
    caches.addAll([
      arbitraryFileCache(
        path: '.gradle-user-home/caches/modules-2/files-2.1',
        cacheName: 'gradle-dependencies',
        cacheValidityDecidingFile: 'gradle/wrapper/gradle-wrapper.properties',
        compressionMethod: 'TARGZ_BEST_SPEED'
      ),
      arbitraryFileCache(
        path: '.gradle-user-home/wrapper/dists',
        cacheName: 'gradle-wrapper',
        cacheValidityDecidingFile: 'gradle/wrapper/gradle-wrapper.properties',
        compressionMethod: 'TARGZ_BEST_SPEED'
      )
    ])
  }

  if (caches) {
    cache(
      maxCacheSize: 2048,
      defaultBranch: 'master',
      caches: caches
    ) {
      if (cypressProject && fileExists('node_modules/.bin/cypress')) {
        String nvmSetup = fileExists('.nvmrc') ? '''
          export NVM_DIR='/home/jenkinsssh/.nvm'
          . /opt/nvm/nvm.sh
          nvm install
        ''' : ''
        sh(
          label: 'Install Cypress binary',
          script: """
            set -e
            ${nvmSetup}
            node_modules/.bin/cypress install
          """.stripIndent()
        )
      }
      body()
    }
    return
  }

  echo 'Build cache enabled, but no supported lock or wrapper file was found'
  body()
}

private boolean usesCypress() {
  if (!fileExists('package.json')) {
    return false
  }

  def packageJson = readJSON(file: 'package.json')
  return packageJson.dependencies?.containsKey('cypress') ||
    packageJson.devDependencies?.containsKey('cypress')
}
