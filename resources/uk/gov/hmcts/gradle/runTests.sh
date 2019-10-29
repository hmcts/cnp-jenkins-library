
[ "$TEST_URL" == "" ] && echo "Error: cannot find TEST_URL env var." && exit 1

[ "$TEST_HEATH_URL" == "" ] && TEST_HEALTH_URL="${TEST_URL}/health"

# smoke or functional
[ "$TASK" == "" ] && TASK="smoke"

_healthy="false"

for i in $(seq 0 30)
do
  sleep 10
  wget -O - $TEST_HEALTH_URL >/dev/null
  [ "$?" == "0" ] && _healthy="true" && break
done

[ "$_healthy" != "true" ] \
  && "Error: application does not seem to be running, check the application logs to see why" \
  && exit 2

sh gradlew --info --rerun-tasks ${TASK}

[ "$?" == "0" ] && _success="true" || _success="false"
if [ "$SLACK_WEBHOOK" != "" ]
then
  if [ "$_success" == "true" ]
  then
    _slackMessage="Gradle Build Successful: TASK = ${TASK} - TEST_URL = ${TEST_URL}"
    _slackIcon=${$SLACK_ICON_SUCCESS:-banana-dance}
  else
    _slackMessage="Gradle Build Failure: TASK = ${TASK} - TEST_URL = ${TEST_URL}"
    _slackIcon=${$SLACK_ICON_FAILURE:-boom}
  fi
  wget --post-data "payload={\"channel\": \"#${SLACK_CHANNEL}\", \"username\": \"${TASK}_test\", \"text\": \"${_slackMessage}\", \"icon_emoji\": \":${_slackIcon}:\"}" \
  $(cat /mnt/secrets/${SLACK_WEBHOOK})
fi

[ "$_success" == "true" ] && exit 0 || exit 1
