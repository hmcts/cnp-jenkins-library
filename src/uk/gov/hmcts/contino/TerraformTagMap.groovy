package uk.gov.hmcts.contino

class TerraformTagMap {

  Map tags

  TerraformTagMap(Map tags) {
    this.tags = tags
  }

  String toString() {

    def output = new StringWriter()
    def separator = ""

    output << '{'

    this.tags.each { k,v ->
      output << separator << k << '=' << '"' << v << '"'
      separator = ','
    }

    output << '}'
    return output.toString()
  }

}
