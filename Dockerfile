FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x ./mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -ntp package

FROM eclipse-temurin:21-jre

RUN groupadd --system --gid 10001 ledgerline \
    && useradd --system --uid 10001 --gid ledgerline --home-dir /app \
       --shell /usr/sbin/nologin ledgerline
WORKDIR /app

COPY --from=build --chown=10001:10001 /workspace/target/ledgerline-*.jar /app/ledgerline.jar

USER 10001:10001
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["sh", "-c", "exec java -jar /app/ledgerline.jar --server.port=${PORT:-8080}"]
