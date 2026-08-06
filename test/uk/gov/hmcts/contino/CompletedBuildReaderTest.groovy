package uk.gov.hmcts.contino

import org.junit.Test

import static org.assertj.core.api.Assertions.assertThat

class CompletedBuildReaderTest {

  @Test
  void ignoresNullActionsWhenReadingTestMetadata() {
    def testResult = new TestResultAction(totalCount: 12, failCount: 2, skipCount: 3)
    def build = [getAllActions: { [null, new Object(), testResult] }]

    def metadata = CompletedBuildReader.testMetadata(build)

    assertThat(metadata).isEqualTo([
      totalCount: 12,
      failCount: 2,
      skipCount: 3,
      passCount: 7
    ])
  }

  private static class TestResultAction {
    int totalCount
    int failCount
    int skipCount
  }
}
