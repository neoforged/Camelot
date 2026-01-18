FROM eclipse-temurin:21-jdk

ENV DEBIAN_FRONTEND=noninteractive

# Install Tesseract
RUN apt-get update && apt-get install -y \
    tesseract-ocr \
    wget \
    libleptonica-dev \
    && rm -rf /var/lib/apt/lists/*

# Create tessdata_best directory
RUN mkdir -p /usr/share/tesseract-ocr/4.00/tessdata_best

# Download tessdata_best English model
RUN wget -O /usr/share/tesseract-ocr/4.00/tessdata_best/eng.traineddata \
    https://github.com/tesseract-ocr/tessdata_best/raw/main/eng.traineddata

# Point Tesseract (and Tess4J) to tessdata_best
ENV TESSDATA_PREFIX=/usr/share/tesseract-ocr/4.00/tessdata_best

COPY build/libs/camelot-all.jar /camelot.jar
VOLUME ["/home/camelot"]
WORKDIR /home/camelot
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=90", "--enable-preview", "-jar", "/camelot.jar"]
