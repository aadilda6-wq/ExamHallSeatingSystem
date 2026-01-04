FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY src ./src
COPY data ./data
COPY static ./static

RUN javac src/*.java

EXPOSE 8080

CMD ["java", "-cp", "src", "SeatingWebServer"]
