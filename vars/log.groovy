#!groovy

def info(message) {
  echo "\u001B[34mINFO: ${message}\u001B[0m"
}

def warning(message) {
  ansiColor('xterm') {
    echo "\u001B[35mWARNING: ${message}\u001B[0m"
  }
}

def error(message) {
  ansiColor('xterm') {
    echo "\u001B[31mERROR: ${message}\u001B[0m"
  }
}
