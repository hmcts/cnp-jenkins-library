#!/bin/sh

[ "$TEST_URL" == "" ] && echo "Error: cannot find TEST_URL env var." && exit 1

[ "$TEST_HEATH_URL" == "" ] && TEST_HEALTH_URL="${TEST_URL}/health"

# smoke or functional
[ "$TASK" == "" ] && TASK="smoke"

_healthy="false"

for i in $(seq 0 30)
do
  sleep 10
  wget $TEST_HEALTH_URL
  [ "$?" == "0" ] && _healthy="true" && break
done

[ "$_healthy" != "true" ] \
  && "Error: application does not seem to be running, check the application logs to see why" \
  && exit 2

./gradlew --info --rerun-tasks ${TASK}
