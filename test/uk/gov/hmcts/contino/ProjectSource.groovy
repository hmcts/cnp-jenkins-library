package uk.gov.hmcts.contino

import com.lesfurets.jenkins.unit.global.lib.SourceRetriever
import groovy.transform.CompileStatic
import groovy.transform.Immutable

@Immutable
@CompileStatic
class ProjectSource implements SourceRetriever {

  String sourceURL

  @Override
  List<URL> retrieve(String repository, String branch, String targetPath) {
    def sourceDir = new File(sourceURL)
    if (sourceDir.exists()) {
      return [sourceDir.toURI().toURL()]
    }
    throw new IllegalStateException("Directory $sourceDir.path does not exists")
  }

  static ProjectSource projectSource(String source) {
    new ProjectSource(source)
  }

  @Override
  String toString() {
    return "ProjectSource{" +
      "sourceURL='" + sourceURL + '\'' +
      '}'
  }
}
