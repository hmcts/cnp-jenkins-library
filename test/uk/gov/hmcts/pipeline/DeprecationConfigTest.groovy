package uk.gov.hmcts.pipeline

class DeprecationConfigTest {

  static def response = ["content": ["terraform":
                                       ["terraform"                              :
                                          ["version": "1.3.4", "date_deadline": "2024-12-30"],
                                        "registry.terraform.io/hashicorp/azurerm": ["version": "3.0.0", "date_deadline": "2024-12-30"]],
                                     "helm"     : ["java": ["version": "5.2.0", "date_deadline": "2024-06-30"]],
                                     "gradle"   : ["java-logging": ["version": "6.0.1", "date_deadline": "2023-10-28"]],
                                     "npm"      : ["angular/core": ["version": "15", "date_deadline": "2024-03-25"]]]
  ]
}
