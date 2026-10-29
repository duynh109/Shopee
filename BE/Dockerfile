# Image chạy BE. Hai giai đoạn: giai đoạn đầu có JDK + Maven để build jar, giai đoạn sau chỉ chép jar sang một
# image chỉ có JRE — image cuối không mang theo mã nguồn, Maven hay thư viện build, nên nhỏ và ít thứ để tấn công.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
# Chép pom trước và tải dependency thành một layer riêng: chỉ sửa code thì Docker dùng lại layer này (CACHED),
# không tải lại cả trăm MB thư viện.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline
COPY src src
# Bỏ test lúc build image: test cần một MySQL đang chạy, mà lúc build chưa có.
RUN ./mvnw -B -q -DskipTests package && cp target/*.jar app.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Chạy bằng user thường, không phải root. Thư mục uploads tạo sẵn và thuộc user đó để volume gắn vào ghi được.
RUN adduser -S -u 10001 app && mkdir uploads && chown app uploads
COPY --from=build /src/app.jar app.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
