package withPipeline

class LibraryBranchAllowlistTest {

  static def response = [
    'content': '''branches:
  - name: master
    allowed: true
  - name: main
    allowed: true
'''
  ]
}