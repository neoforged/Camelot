FROM openjdk:21
COPY build/libs/camelot-all.jar /camelot.jar
VOLUME ["/home/camelot"]
WORKDIR /home/camelot
RUN cd /home/camelot
ENTRYPOINT ["java", "--enable-preview", "-jar", "/camelot.jar"]