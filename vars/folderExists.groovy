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
  if (Files.exists(folderPath)) {
    return block.call()
  }
}
