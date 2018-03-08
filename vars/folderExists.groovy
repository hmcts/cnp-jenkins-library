import java.nio.file.Files
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

  path = FileSystems.getDefault().getPath(localPath, folderPath)
  log.info("looking for path: $path")
  if (fileExists(path))
    return block.call()
  /*if (Files.exists(FileSystems.getDefault().getPath(localPath, folderPath))) {
    return block.call()
  }*/
  else
    log.info("$folderPath not found => There is no infrastructure to build")
}
