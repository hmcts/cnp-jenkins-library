/**
 * Stage with Agent selection. Stages run in parallel on separate agents
 * if docker containers are used. Otherwise in parallel on the same vm agent.
 *
 */
def call(String name, Map<String, Tuple2<Closure, Closure>> stages, boolean failFast, boolean noSkip) {
  stage(name) {
    when(noSkip) {
      withDockerParallelAgent(stages, failFast)
    }
  }
}
