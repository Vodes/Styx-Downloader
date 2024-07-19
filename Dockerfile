FROM azul/zulu-openjdk-alpine:21 as BUILD

COPY . /app/
WORKDIR /app

RUN ./gradlew clean shadow-ci

FROM azul/zulu-openjdk-alpine:21-jre

COPY --from=BUILD /app/app.jar /app
WORKDIR /app

ENTRYPOINT ["java", "-jar", "app.jar"]