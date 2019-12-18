
[ "$TEST_URL" == "" ] && echo "Error: cannot find TEST_URL env var." && exit 1

[ "$TEST_HEATH_URL" == "" ] && TEST_HEALTH_URL="${TEST_URL}/health"

# smoke or functional
_task=${TASK:-smoke}
_type=${TASK_TYPE}

_healthy="false"

for i in $(seq 0 30)
do
  sleep 10
  wget -O - "$TEST_HEALTH_URL" >/dev/null
  [ "$?" == "0" ] && _healthy="true" && break
done

[ "$_healthy" != "true" ] \
  && "Error: application does not seem to be running, check the application logs to see why" \
  && exit 2

# Export secrets from flexvolumes as environment variables
for i in "$@"
do
  [ -z "${i##*=*}" ] && _secret=$(echo "$i"| cut -d '=' -f 2) && export $(echo "$i"| cut -d '=' -f 1)=$(cat "${_secret}")
done

export GRADLE_OPTS="-XX:MaxMetaspaceSize=256m -XX:+HeapDumpOnOutOfMemoryError -Xms256m -Xmx768m -Dfile.encoding=UTF-8"
sh gradlew --info  --init-script init.gradle --rerun-tasks "$_task"

[ "$?" == "0" ] && _success="true" || _success="false"

# Notify slack channel
if [ "$SLACK_WEBHOOK" != "" ] && [ "$SLACK_CHANNEL" != "" ]
then
  _slackNotifySuccess=${SLACK_NOTIFY_SUCCESS:-true}
  if [ "$_success" == "true" ]
  then
      _slackDefaultMessage="Gradle Build Success: ${CLUSTER_NAME} ${TEST_URL}" \
      _slackMessage=${SLACK_MESSAGE_SUCCESS:-$_slackDefaultMessage}
    _slackIcon=${SLACK_ICON_SUCCESS:-banana-dance}
  else
      _slackDefaultMessage="Gradle Build Failure: ${CLUSTER_NAME} ${TEST_URL}" \
      _slackMessage=${SLACK_MESSAGE_FAILURE:-$_slackDefaultMessage}
    _slackIcon=${SLACK_ICON_FAILURE:-boom}
  fi
  if [ "$_success" == "false" ] || [ "$_slackNotifySuccess" == "true" ]
  then
    wget --post-data "payload={\"channel\": \"#${SLACK_CHANNEL}\", \"username\": \"${_task}${_type}\", \"text\": \"${_slackMessage}\", \"icon_emoji\": \":${_slackIcon}:\"}" \
      "$SLACK_WEBHOOK"
  fi
fi

[ "$_success" == "true" ] && exit 0 || exit 1
