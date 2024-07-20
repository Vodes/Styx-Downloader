FROM azul/zulu-openjdk-alpine:21.0.4-jdk as BUILD

COPY . /app/
WORKDIR /app
RUN chmod +x ./gradlew
RUN ./gradlew clean shadow-ci --no-daemon

FROM azul/zulu-openjdk-debian:21.0.4-jre

RUN apt-get -qq update && apt-get -qq -y install git python3 python3-pip python-is-python3
RUN pip3 install --break-system-packages --user git+https://github.com/Vodes/muxtools-styx.git

COPY --from=BUILD /app/app.jar .

ENTRYPOINT ["java", "-jar", "app.jar"]