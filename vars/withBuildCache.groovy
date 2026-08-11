#!groovy

def call(config, Closure body) {
  if (!config.buildCache) {
    body()
    return
  }

  if (fileExists('yarn.lock')) {
    cache(
      maxCacheSize: 2048,
      defaultBranch: 'master',
      caches: [
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
      ]
    ) {
      body()
    }
    return
  }

  if (fileExists('gradlew')) {
    env.GRADLE_USER_HOME = "${env.WORKSPACE}/.gradle-user-home"
    cache(
      maxCacheSize: 2048,
      defaultBranch: 'master',
      caches: [
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
      ]
    ) {
      body()
    }
    return
  }

  echo 'Build cache enabled, but no supported lock or wrapper file was found'
  body()
}
