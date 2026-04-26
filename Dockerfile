FROM node:22.16.0-alpine3.20 AS front-builder

WORKDIR /opt/veriguard-build/veriguard-front
COPY veriguard-front/packages ./packages
COPY veriguard-front/patches ./patches
COPY veriguard-front/package.json veriguard-front/yarn.lock veriguard-front/.yarnrc.yml ./
RUN npm install -g corepack
RUN yarn install
COPY veriguard-front /opt/veriguard-build/veriguard-front
RUN yarn build

FROM maven:3.9.14-eclipse-temurin-21 AS api-builder

WORKDIR /opt/veriguard-build/veriguard
COPY veriguard-model ./veriguard-model
COPY veriguard-framework ./veriguard-framework
COPY veriguard-api ./veriguard-api
COPY pom.xml ./pom.xml
COPY --from=front-builder /opt/veriguard-build/veriguard-front/builder/prod/build ./veriguard-front/builder/prod/build
RUN mvn install -DskipTests -Pdev

FROM eclipse-temurin:21.0.10_7-jre AS app

RUN DEBIAN_FRONTEND=noninteractive apt-get update -q && DEBIAN_FRONTEND=noninteractive apt-get install -qq -y tini && rm -rf /var/lib/apt/lists/*
COPY --from=api-builder /opt/veriguard-build/veriguard/veriguard-api/target/veriguard-api.jar ./

ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["java", "-jar", "veriguard-api.jar"]
