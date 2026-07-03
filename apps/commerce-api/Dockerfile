# 부하 측정용 commerce-api SUT 이미지. 빌드 컨텍스트 = 레포 루트.
#   docker build -f apps/commerce-api/Dockerfile -t commerce-api:perf .
# (멀티모듈이라 컨텍스트가 루트여야 modules/supports 를 함께 빌드한다.)

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
# 부하 SUT 이미지 — 테스트는 CI 가 담당, 여기선 제외해 빌드 시간 단축
RUN chmod +x gradlew && ./gradlew :apps:commerce-api:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
# apps 모듈은 plain jar 비활성 → build/libs 에 boot jar 하나뿐이라 glob 안전
COPY --from=build /workspace/apps/commerce-api/build/libs/*.jar app.jar
# 힙·GC 고정 옵션은 compose 의 JAVA_OPTS 로 주입 (측정마다 동일 조건 유지)
ENV JAVA_OPTS=""
# 8080 = API, 8081 = actuator(management.server.port)
EXPOSE 8080 8081
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
