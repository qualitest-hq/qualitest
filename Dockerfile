# 质衡后端（qualitest-admin）多阶段构建
# 构建上下文：仓库根目录

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /src

COPY pom.xml .
COPY qualitest-admin/pom.xml qualitest-admin/
COPY qualitest-common/pom.xml qualitest-common/
COPY qualitest-framework/pom.xml qualitest-framework/
COPY qualitest-system/pom.xml qualitest-system/
COPY qualitest-quartz/pom.xml qualitest-quartz/
COPY qualitest-generator/pom.xml qualitest-generator/

RUN mvn -B -q -DskipTests dependency:go-offline || true

COPY qualitest-admin qualitest-admin
COPY qualitest-common qualitest-common
COPY qualitest-framework qualitest-framework
COPY qualitest-system qualitest-system
COPY qualitest-quartz qualitest-quartz
COPY qualitest-generator qualitest-generator

RUN mvn -B -DskipTests -pl qualitest-admin -am package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /src/qualitest-admin/target/qualitest-admin.jar /app/app.jar

ENV JAVA_OPTS="-Xms256m -Xmx1024m -Duser.timezone=Asia/Shanghai" \
    SPRING_PROFILES_ACTIVE=docker \
    QUALITEST_PROFILE=/data/upload

RUN mkdir -p /data/upload

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=10 \
  CMD curl -fsS "http://127.0.0.1:8080/captchaImage" >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
