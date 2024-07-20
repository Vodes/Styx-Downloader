FROM azul/zulu-openjdk-alpine:21.0.4-jdk as BUILD

COPY . /app/
WORKDIR /app
RUN chmod +x ./gradlew
RUN ./gradlew clean shadow-ci --no-daemon

FROM azul/zulu-openjdk-alpine:21.0.4-jre

COPY --from=BUILD /app/app.jar .

ENTRYPOINT ["java", "-jar", "app.jar"]