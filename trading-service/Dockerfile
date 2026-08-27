# 1단계: JDK 환경에서 Gradle 빌드 수행
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 루트 디렉터리의 Gradle 설정 및 소스 복사
COPY . .
RUN ./gradlew :trading-service:bootJar -x test

# 2단계: 경량 JRE 환경으로 결과물(jar)만 가져와서 실행
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/trading-service/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]