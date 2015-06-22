FROM maven:3.2-jdk-7  

#RUN apt-get update && apt-get install -y apt-utils zip unzip

RUN mkdir -p /app

WORKDIR /app

ADD . /app/

# Build and run tests, exit 1 if it fails
RUN ["/bin/sh", "./container-debug.sh"]

#RUN zip /app/app.zip /app/*
