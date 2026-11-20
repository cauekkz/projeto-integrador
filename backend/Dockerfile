# Backend (Spring Boot). Dois estagios: compila com o JDK e roda so com o JRE.
#   docker build -t vanroute-backend ./backend
# Configuracao toda por variavel de ambiente (veja o .env.example na raiz).

# 1) compila. As dependencias primeiro: se so o codigo mudar, o Docker reaproveita essa camada
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package && cp target/backend-*.jar /app.jar

# 2) roda. O Tesseract (OCR da CNH) e uma biblioteca nativa: vem do sistema. As fontes servem pro PDFBox
#    desenhar a CNH antes do OCR. O idioma (por.traineddata) ja vai dentro do jar
FROM eclipse-temurin:21-jre-noble
RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr fontconfig fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 vanroute
COPY --from=build /app.jar /app/app.jar
USER vanroute
EXPOSE 9090
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
