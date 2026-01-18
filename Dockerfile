FROM eclipse-temurin:21-jdk

# Prevent interactive prompts
ENV DEBIAN_FRONTEND=noninteractive

# Install Tesseract OCR + English language pack
RUN apt-get update && apt-get install -y \
    tesseract-ocr \
    tesseract-ocr-eng \
    && rm -rf /var/lib/apt/lists/*

COPY build/libs/camelot-all.jar /camelot.jar
VOLUME ["/home/camelot"]
WORKDIR /home/camelot
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=90", "--enable-preview", "-jar", "/camelot.jar"]
