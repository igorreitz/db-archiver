FROM alpine AS build-stage
COPY ./build/distributions/*.zip .
RUN mkdir distr && unzip -qd distr *.zip && rm *.zip

FROM openjdk:8-jdk-alpine
WORKDIR /opt/apps/db-archiver
COPY --from=build-stage /distr/lib ./lib/
COPY --from=build-stage /distr/*jar ./

ENTRYPOINT [    \
    "java",     \
    "-Xmx384M", \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Duser.timezone=Europe/Moscow",    \
    "-jar", "db-archiver.jar" \
]