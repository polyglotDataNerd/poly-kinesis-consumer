FROM openjdk:8-jdk
MAINTAINER Gerard Bartolome (gerard.bartolome@bod.com)

ARG MAVEN_VERSION=3.6.3
ARG USER_HOME_DIR="/root"
ARG SHA=c35a1803a6e70a126e80b2b3ae33eed961f83ed74d18fcd16909b2d44d7dada3203f1ffe726c17ef8dcca2dcaa9fca676987befeadc9b9f759967a8cb77181c0
ARG BASE_URL=https://apache.osuosl.org/maven/maven-3/${MAVEN_VERSION}/binaries
ARG AWS_ACCESS_KEY_ID
ARG AWS_SECRET_ACCESS_KEY
ARG AWS_REGION=us-west-2
ARG GitToken
ARG GitHash
ARG environment
ENV ENVIORMENT=${environment}

RUN mkdir -p /usr/share/maven /usr/share/maven/ref \
  && curl -fsSL -o /tmp/apache-maven.tar.gz ${BASE_URL}/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
  && echo "${SHA}  /tmp/apache-maven.tar.gz" | sha512sum -c - \
  && tar -xzf /tmp/apache-maven.tar.gz -C /usr/share/maven --strip-components=1 \
  && rm -f /tmp/apache-maven.tar.gz \
  && ln -s /usr/share/maven/bin/mvn /usr/bin/mvn \
  && apt-get update && apt-get -y install python-pip python-yaml python-dev \
  && pip install awscli \
  && curl -o /usr/local/bin/ecs-cli https://s3.amazonaws.com/amazon-ecs-cli/ecs-cli-linux-amd64-v1.5.0 \
  && chmod +x /usr/local/bin/ecs-cli \
  && apt-get -y install vim \
  && aws --version \
  && aws configure set aws_access_key_id ${AWS_ACCESS_KEY_ID} \
  && aws configure set aws_secret_access_key ${AWS_SECRET_ACCESS_KEY} \
  && aws configure set default.region ${AWS_REGION}

#installs python3/pip3
RUN apt-get update -y && \
  apt-get install python3-pip idle3 -y && \
  pip3 install --no-cache-dir --upgrade pip && \
  \
  # delete cache and tmp files
  apt-get clean && \
  apt-get autoclean && \
  rm -rf /var/cache/* && \
  rm -rf /tmp/* && \
  rm -rf /var/tmp/* && \
  rm -rf /var/lib/apt/lists/*
RUN git config --global url."https://${GitToken}:@github.com/".insteadOf "https://github.com/"
ENV MAVEN_HOME /usr/share/maven
ENV MAVEN_CONFIG "$USER_HOME_DIR/.m2"

COPY mvn-entrypoint.sh /usr/local/bin/mvn-entrypoint.sh
COPY settings-docker.xml /usr/share/maven/ref/
RUN chmod +x /usr/local/bin/mvn-entrypoint.sh

ENTRYPOINT ["/usr/local/bin/mvn-entrypoint.sh"]
RUN mvn --version \
    && mkdir -p usr/local/service/kcl/ \
    && cd /usr/local/service/kcl \
    && git clone -b master https://github.com/beachbodydigital/poly-kinesis-consumer \
    && cd poly-kinesis-consumer \
    && mvn package
RUN cd \
    && mkdir kcl \
    && cd kcl \
    && cp /usr/local/service/kcl/poly-kinesis-consumer/target/*.jar . \
    && aws s3 cp s3://poly-utils/kcl/refactor/ /root/kcl --recursive --sse --quiet --exclude "*" --include "log4j.properties" --include "${environment}-config.properties"

WORKDIR /root/kcl

RUN cd /root/kcl  \
    && pwd \
    && mv "${environment}-config.properties" "config.properties" \
    # https://www.tutorialspoint.com/log4j/log4j_patternlayout.htm
    # add log4j logging pattern layout to properties file. The java -D option for setting a property takes a variable passed as a literal
    && echo "log4j.appender.logfile.layout.ConversionPattern = %d{yyyy-MM-dd HH:mm:ss,SSS} %p ${environment} thread:%t [%F:%M:%C{1}:%L] %m%n" >> log4j.properties \
    && echo "log4j.appender.stderr.layout.ConversionPattern = %d{yyyy-MM-dd HH:mm:ss,SSS} %p ${environment} thread:%t [%F:%M:%C{1}:%L] %m%n" >> log4j.properties \
    && echo "log4j.appender.stdout.layout.ConversionPattern = %d{yyyy-MM-dd HH:mm:ss,SSS} %p ${environment} thread:%t [%F:%M:%C{1}:%L] %m%n" >> log4j.properties \
    && echo "#!/usr/bin/env bash\njava -Xms20G -Xmx20G -XX:-UseGCOverheadLimit -jar /root/kcl/poly-kinesis-consumer-1.0-ecs.jar ${environment}\n " >> run.sh \
    && chmod +x run.sh

CMD ["./run.sh"]
