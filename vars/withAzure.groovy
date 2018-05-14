#!groovy

import uk.gov.hmcts.contino.azure.AzureFactory

def call(String subscription, Closure body) {
  def azure = AzureFactory.getAzure(subscription)
  body.call(azure)
}
