
set -x

[ "$TEST_URL" == "" ] && echo "Error: cannot find TEST_URL env var." && exit 1

[ "$TEST_HEATH_URL" == "" ] && TEST_HEALTH_URL="${TEST_URL}/health"

# smoke or functional
_task=${TASK:-smoke}

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

sh gradlew --info --rerun-tasks ${_task}

[ "$?" == "0" ] && _success="true" || _success="false"
if [ "$SLACK_WEBHOOK" != "" ]
then
  _slackNotifySuccess=${SLACK_NOTIFY_SUCCESS:-true}
  if [ "$_success" == "true" ]
  then
      _slackDefaultMessage="Gradle Build Successful: TASK = ${_task} - TEST_URL = ${TEST_URL}" \
      _slackMessage=${SLACK_MESSAGE_SUCCESS:-$_slackDefaultMessage}
    _slackIcon=${SLACK_ICON_SUCCESS:-banana-dance}
  else
      _slackDefaultMessage="Gradle Build Failure: TASK = ${_task} - TEST_URL = ${TEST_URL}" \
      _slackMessage=${SLACK_MESSAGE_FAILURE:-$_slackDefaultMessage}
    _slackIcon=${SLACK_ICON_FAILURE:-boom}
  fi
  if [ "$_success" == "false" ] || [ "$_slackNotifySuccess" == "true" ]
  then
    wget --post-data "payload={\"channel\": \"#${SLACK_CHANNEL}\", \"username\": \"${_task}_test\", \"text\": \"${_slackMessage}\", \"icon_emoji\": \":${_slackIcon}:\"}" \
    $(cat "/mnt/secrets/${SLACK_WEBHOOK}")
  fi
fi

[ "$_success" == "true" ] && exit 0 || exit 1
