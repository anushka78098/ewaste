# E-Waste Manager server image: build stage compiles the server, runtime stage is a slim JRE running as a non-root user.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY src ./src
# Only the server side is needed in the image (common + model + server); the desktop clients run on people's computers.
RUN mkdir /classes && javac -d /classes $(find src/com/ewaste/common src/com/ewaste/model src/com/ewaste/server -name '*.java')
ARG SQLITE_VERSION=3.36.0.3
ARG POSTGRES_VERSION=42.7.14
RUN mkdir /lib-out && cd /lib-out \
 && BASE=https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/${SQLITE_VERSION} \
 && curl -fsSL -o sqlite-jdbc.jar "$BASE/sqlite-jdbc-${SQLITE_VERSION}.jar" \
 && echo "$(curl -fsSL $BASE/sqlite-jdbc-${SQLITE_VERSION}.jar.sha1)  sqlite-jdbc.jar" | sha1sum -c - \
 && curl -fsSL -o postgresql-${POSTGRES_VERSION}.jar \
      "https://repo.maven.apache.org/maven2/org/postgresql/postgresql/${POSTGRES_VERSION}/postgresql-${POSTGRES_VERSION}.jar" \
 && echo "73914527305A40CCE504B0D3D90B23CAF565136912D607AE8AE7C5895512332C  postgresql-${POSTGRES_VERSION}.jar" | sha256sum -c -

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --home-dir /app --shell /usr/sbin/nologin ewaste \
 && mkdir -p /app /data && chown ewaste /data
WORKDIR /app
COPY --from=build /classes /app/classes
COPY --from=build /lib-out /app/lib
USER ewaste
ENV EWASTE_HOST=0.0.0.0 \
    EWASTE_PORT=8080 \
    EWASTE_DATA=/data \
    EWASTE_STORE=auto \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70"
VOLUME /data
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD ["java", "-cp", "/app/classes", "com.ewaste.server.HealthCheck"]
ENTRYPOINT ["java", "-cp", "/app/classes:/app/lib/*", "com.ewaste.server.ServerMain"]
