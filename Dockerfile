FROM vodes/styx-baseimage:v2

COPY ./app.jar .

ENTRYPOINT ["java", "-jar", "app.jar"]