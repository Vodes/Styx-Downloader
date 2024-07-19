FROM azul/zulu-openjdk-alpine:21 as BUILD

COPY . /app/
WORKDIR /app
RUN chmod +x ./gradlew
RUN ./gradlew clean shadow-ci

FROM azul/zulu-openjdk-alpine:21-jre

COPY --from=BUILD /app/app.jar .

ENTRYPOINT ["java", "-jar", "app.jar"]