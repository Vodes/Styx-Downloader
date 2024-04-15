FROM azul/zulu-openjdk-alpine:17-latest as runner

RUN apk add gradle

WORKDIR /app

COPY . .

ENTRYPOINT ["gradle run"]