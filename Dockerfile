FROM eclipse-temurin:21
COPY build/libs/camelot-all.jar /camelot.jar
VOLUME ["/home/camelot"]
WORKDIR /home/camelot
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=90", "--enable-preview", "-jar", "/camelot.jar"]
