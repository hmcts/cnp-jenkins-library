FROM groovy:jre8
COPY gradle ./gradle
COPY resources ./resources
COPY build.gradle gradlew gradlew.bat init.gradle settings.gradle ./
RUN ["./gradlew", "build"]
