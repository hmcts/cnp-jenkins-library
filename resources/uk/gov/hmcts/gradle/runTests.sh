
[ "$TEST_URL" == "" ] && echo "Error: cannot find TEST_URL env var." && exit 1

[ "$TEST_HEATH_URL" == "" ] && TEST_HEALTH_URL="${TEST_URL}/health"

# smoke or functional
_task=${TASK:-smoke}
_type=${TASK_TYPE}
_success_timeout=${SUCCESS_TIMEOUT:-21600}   # 6 hours default

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

export GRADLE_OPTS="-XX:MaxMetaspaceSize=256m -XX:+HeapDumpOnOutOfMemoryError -Xms256m -Xmx2048m -Dfile.encoding=UTF-8"
sh gradlew --info --rerun-tasks "$_task"

[ "$?" == "0" ] && _success="true" || _success="false"

TEST_HOST=$(echo "$TEST_URL" |sed 's!^https*://!!' |sed 's![^-_a-zA-Z0-9]!_!g')

wget -O kubectl https://storage.googleapis.com/kubernetes-release/release/v1.16.4/bin/linux/amd64/kubectl
chmod 750 kubectl

hasConfigMap=$(./kubectl get configmap test-runs-configmap)
currentDate=$(date '+%s')
currentRun="${_success};1;${currentDate}"
hostAndTask="${TEST_HOST}-${_task}"

if [ "$hasConfigMap" != "" ]
then
  previousRun=$(./kubectl get configmap test-runs-configmap -o jsonpath='{.data.'${hostAndTask}'}')
  if [ "$previousRun" != "" ]
  then
    previousSuccess=$(echo "$previousRun" |cut -d";" -f1)
    previousCount=$(echo "$previousRun" |cut -d";" -f2)
    previousDate=$(echo "$previousRun" |cut -d";" -f3)
    if [ "$previousSuccess" != "true" ]
    then
      if [ "$_success" == "true" ]
      then
        if [ $(($currentDate - $previousDate)) -lt "$_success_timeout" ]
        then
          export SLACK_MESSAGE_SUCCESS="Gradle Build Fixed: ${CLUSTER_NAME} ${TEST_URL}"
          export SLACK_NOTIFY_SUCCESS=true
        fi
      else
        currentRun="${_success};$(($previousCount + 1));${currentDate}"
      fi
    fi
  fi
  ./kubectl patch configmap test-runs-configmap --type merge -p '{"data":{"'${hostAndTask}'":"'"${currentRun}"'"}}'
else
  ./kubectl create configmap test-runs-configmap --from-literal="${hostAndTask}"="${currentRun}"
fi

# Notify slack channel
if [ "$SLACK_WEBHOOK" != "" ] && [ "$SLACK_CHANNEL" != "" ]
then
  _slackNotifySuccess=${SLACK_NOTIFY_SUCCESS:-true}
  if [ "$_success" == "true" ]
  then
    _slackDefaultMessage="Gradle Build Success: ${CLUSTER_NAME} ${TEST_URL}"
    _slackMessage=${SLACK_MESSAGE_SUCCESS:-$_slackDefaultMessage}
    _slackIcon=${SLACK_ICON_SUCCESS:-green_heart}
  else
    _slackDefaultMessage="Gradle Build Failure: ${CLUSTER_NAME} ${TEST_URL}"
    _slackMessage=${SLACK_MESSAGE_FAILURE:-$_slackDefaultMessage}
    _slackIcon=${SLACK_ICON_FAILURE:-red_circle}
  fi
  if [ "$_success" == "false" ] || [ "$_slackNotifySuccess" == "true" ]
  then
    wget --post-data "payload={\"channel\": \"#${SLACK_CHANNEL}\", \"username\": \"${_task}${_type}\", \"text\": \"${_slackMessage}\", \"icon_emoji\": \":${_slackIcon}:\"}" \
      "$SLACK_WEBHOOK"
  fi
fi

[ "$_success" == "true" ] && exit 0 || exit 1
