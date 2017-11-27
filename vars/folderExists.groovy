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
  if (Files.exists(FileSystems.getDefault().getPath(folderPath))) {
    return block.call()
  }
}
