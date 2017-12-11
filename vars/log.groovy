#!groovy

def info(message) {
  echo "INFO: ${message}"
}

def warning(message) {
  ansiColor {
    echo "\\e[35mWARNING: ${message}\\e[0m"
  }
}

def error(message) {
  ansiColor {
    echo "\\e[31mERROR: ${message}\\e[0m"
  }
}
