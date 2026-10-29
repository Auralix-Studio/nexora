# syntax=docker/dockerfile:1
# NEXORA: imagen de producción (Tomcat 11 + Java 21; el código compila con --release 17).
#
# Construcción normal (con las pruebas JUnit):   docker build -t nexora:local .
# Construcción rápida (sin pruebas):             docker build --build-arg OMITIR_PRUEBAS=true -t nexora:local .
# Con versión visible en /salud:                 docker build --build-arg NEXORA_VERSION=abc1234 -t nexora:abc1234 .

# ---- Etapa 1: construcción del WAR ------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS construccion
WORKDIR /compilacion

# Primero solo el pom.xml: esta capa (dependencias) se reutiliza mientras el pom no cambie.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

# Después el código. Por defecto SE EJECUTAN las pruebas; OMITIR_PRUEBAS=true las salta.
COPY src ./src
ARG OMITIR_PRUEBAS=false
RUN mvn -B package -DskipTests="${OMITIR_PRUEBAS}"

# ---- Etapa 2: imagen final --------------------------------------------------------------------
FROM tomcat:11.0-jdk21-temurin

# Sin aplicaciones de ejemplo ni consola de administración; solo NEXORA como aplicación raíz.
RUN rm -rf /usr/local/tomcat/webapps/* /usr/local/tomcat/webapps.dist

# Usuario sin privilegios (uid 10001). Solo es dueño de lo que Tomcat necesita escribir.
RUN groupadd --system --gid 10001 nexora \
 && useradd --system --uid 10001 --gid 10001 --home-dir /usr/local/tomcat --shell /usr/sbin/nologin nexora \
 && mkdir -p /usr/local/tomcat/conf/Catalina/localhost \
 && chown -R 10001:10001 /usr/local/tomcat/webapps /usr/local/tomcat/work /usr/local/tomcat/temp \
                         /usr/local/tomcat/logs /usr/local/tomcat/conf/Catalina

# Configuración endurecida de Tomcat (puerto de apagado cerrado, sin AJP, RemoteIpValve, access log a stdout).
COPY --chmod=644 infra/tomcat/server.xml /usr/local/tomcat/conf/server.xml

# La aplicación como contexto raíz: http://host:8080/ (sin /Semana04).
COPY --from=construccion --chown=10001:10001 /compilacion/target/Semana04.war /usr/local/tomcat/webapps/ROOT.war

# La versión se declara al final para no invalidar las capas anteriores en cada commit.
ARG NEXORA_VERSION=dev
ENV NEXORA_ENTORNO=produccion \
    NEXORA_VERSION=${NEXORA_VERSION} \
    CATALINA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Duser.timezone=America/Lima"
# DB_URL, DB_USER, DB_PASSWORD y NEXORA_DEMO se inyectan al ejecutar (nunca se guardan en la imagen).

USER 10001:10001
EXPOSE 8080
