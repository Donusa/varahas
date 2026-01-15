FROM eclipse-temurin:17-jre-jammy 
ARG JAR_FILE=target/*.jar 
COPY ${JAR_FILE} varahas-1.0.0.jar 
EXPOSE 8080 
ENTRYPOINT [ "java","-jar","/varahas-1.0.0.jar" ]
