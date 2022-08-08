import java.time.LocalDate

def call(Map<String, String> params) {

  def product = params.product
  def component = params.component

  writeFile file: 'reconcile-flux-image-repository.sh', text: libraryResource('uk/gov/hmcts/flux/reconcile-flux-image-repository.sh')

  try {
    sh """
    chmod +x reconcile-flux-image-repository.sh
    ./reconcile-flux-image-repository.sh $product $component
    """
  } finally {
    sh 'rm -f reconcile-flux-image-repository.sh'
  }

}
