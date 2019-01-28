import java.nio.file.FileSystems

/*
 * folderExists
 *
 * Runs the block of code if the folder exists
 *
 * folderExists('relative/path/to/folder') {
 *   ...
 * }
 */
def call(String folderPath, Closure block) {
  def localPath = sh(script: 'pwd', returnStdout: true).trim()

  path = FileSystems.getDefault().getPath(localPath, folderPath).toString()
  log.info("looking for path: $path")
  if (!fileExists(path))
    return block.call()
}
