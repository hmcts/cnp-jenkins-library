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
  sh "pwd"
  sh "ls -l"
  def localPath = sh "pwd"
  def path = FileSystems.getDefault().getPath(localPath, folderPath)
  echo "${path.toUri()}"
  if (Files.exists(path)) {
    echo "In block"
    return block.call()
  }
}
