#!/usr/bin/env groovy

/**
 * Ensures Docker daemon is available and responsive primarily for before running tests with Testcontainers
 * 
 * 
 * This function:
 * - Checks Docker availability with retries
 * - Uses exponential backoff (5s, 10s, 20s, 30s, 30s)
 * - Fails fast with clear error message if Docker is unavailable
 * - Does NOT pre-pull images (left to individual projects)
 * 
 * @param maxRetries Number of retry attempts (default: 5)
 * @param initialWaitSeconds Initial wait time between retries (default: 5)
 * @return true if Docker is available, throws error otherwise
 */
def call(Map params = [:]) {
    def maxRetries = params.get('maxRetries', 5)
    def initialWaitSeconds = params.get('initialWaitSeconds', 5)
    
    echo "=== Verifying Docker Daemon Availability ==="
    
    def attempt = 1
    def dockerAvailable = false
    def waitSeconds = initialWaitSeconds
    
    while (attempt <= maxRetries && !dockerAvailable) {
        try {
            echo "Docker availability check: attempt ${attempt}/${maxRetries}"
            
            // Check if Docker daemon is responsive
            sh '''
                #!/bin/bash
                set -e
                
                # Verify Docker daemon responds
                docker info > /dev/null 2>&1
                
                # Verify Docker can list containers
                docker ps > /dev/null 2>&1
            '''
            
            dockerAvailable = true
            echo "✓ Docker daemon is available and responsive"
            
            // Log Docker version for debugging
            try {
                def dockerVersion = sh(
                    script: 'docker version --format "{{.Server.Version}}" 2>/dev/null || echo "unknown"',
                    returnStdout: true
                ).trim()
                echo "  Docker version: ${dockerVersion}"
            } catch (Exception e) {
                // Non-fatal, just for informational purposes
                echo "  (Could not determine Docker version)"
            }
            
        } catch (Exception e) {
            if (attempt < maxRetries) {
                echo "⚠ Docker not ready (attempt ${attempt}/${maxRetries})"
                echo "  Error: ${e.message}"
                echo "  Waiting ${waitSeconds} seconds before retry..."
                sleep(waitSeconds)
                
                // Exponential backoff: 5s -> 10s -> 20s -> 30s -> 30s (max)
                waitSeconds = Math.min(waitSeconds * 2, 30)
                attempt++
            } else {
                // Final attempt failed - fail the build with clear message
                error("""
╔════════════════════════════════════════════════════════════════╗
║ Docker Daemon Not Available                                   ║
╚════════════════════════════════════════════════════════════════╝

Docker daemon is not responding after ${maxRetries} attempts.

This will cause Testcontainers to fail with:
  "Could not find a valid Docker environment"

Possible causes:
  1. Docker daemon not running on Jenkins agent
  2. Docker socket not accessible (permissions issue)
  3. Jenkins agent experiencing high load
  4. Docker daemon crashed or restarting

Build cannot continue without Docker availability.
""")
            }
        }
    }
    
    return dockerAvailable
}