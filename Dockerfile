FROM eclipse-temurin:21-jdk

ENV DEBIAN_FRONTEND=noninteractive

# Build dependencies
RUN apt-get update && apt-get install -y \
    autoconf automake libtool pkg-config \
    g++ make \
    libjpeg-dev libpng-dev libtiff-dev zlib1g-dev \
    wget

# Download and build Leptonica 1.87.0
RUN wget https://github.com/DanBloomberg/leptonica/releases/download/1.87.0/leptonica-1.87.0.tar.gz && \
    tar -xzf leptonica-1.87.0.tar.gz && \
    cd leptonica-1.87.0 && \
    ./configure && \
    make -j$(nproc) && \
    make install && \
    ldconfig && \
    cd .. && rm -rf leptonica-1.87.0*

# Install Tesseract
RUN apt-get update && apt-get install -y \
    tesseract-ocr \
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
