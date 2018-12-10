FROM groovy:jre8-alpine
COPY gradle ./gradle
COPY resources ./resources
COPY build.gradle gradlew gradlew.bat init.gradle settings.gradle ./
RUN ["./gradlew", "build"]
