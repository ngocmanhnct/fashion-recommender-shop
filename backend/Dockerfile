# Bước 5 Tuần 4 — image cho backend Spring Boot.
# 2 giai đoạn: (1) build bằng JDK + Maven Wrapper có sẵn trong repo, (2) chỉ chép file .jar
# sang image JRE gọn hơn (không cần trình biên dịch lúc chạy).

# ---- Giai đoạn 1: build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Chép riêng file Maven trước, tải dependency trước khi chép mã nguồn: sửa code mà không sửa
# pom.xml thì Docker dùng lại lớp cache này, không tải lại toàn bộ thư viện.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sh ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Bỏ test khi đóng gói image: test chạy ở CI (Bước 6), test có ngữ cảnh Spring cần MySQL
# thật mà lúc build image chưa có.
RUN sh ./mvnw -B -q package -DskipTests

# ---- Giai đoạn 2: chạy ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
