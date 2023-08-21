FROM openjdk:17
COPY build/libs/camelot-all.jar /camelot.jar
VOLUME ["/home/camelot"]
WORKDIR /home/camelot
RUN cd /home/camelot
ENTRYPOINT ["java", "-jar", "/camelot.jar"]