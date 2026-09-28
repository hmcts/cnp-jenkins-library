package withPipeline

class LibraryTagsTest {

  static def response = [
    content: '[{"name":"1.1.1","zipball_url":"url","tarball_url":"url","commit":{"sha":"sha","url":"url"},"node_id":"nodeid"},' +
      '{"name":"2.9.0","zipball_url":"url","tarball_url":"url","commit":{"sha":"sha","url":"url"},"node_id":"nodeid"}]'
  ]
}