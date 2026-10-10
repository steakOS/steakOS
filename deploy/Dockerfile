# steakOS runtime image
# Tag with the product stamp (VERSIONS.md V7), e.g.:
#   docker build -t steakos:SBM-R1.0-V0.1.1-20260315T1321 .
# Or use scripts/docker-tag.sh after a local build.
#
# Pre-GA: software V is 0.x (build.gradle). At SBM GA flip to 1.0.0.

FROM eclipse-temurin:17-jre-jammy

ARG STEAKOS_PRODUCT_LINE=SBM
ARG STEAKOS_RELEASE_MAJOR=1
ARG STEAKOS_RELEASE_MINOR=0
ARG STEAKOS_BUILD_ID=
ARG STEAKOS_VERSION=0.1.1

LABEL org.opencontainers.image.title="steakOS" \
      org.opencontainers.image.description="steakOS distribution" \
      steakos.productLine="${STEAKOS_PRODUCT_LINE}" \
      steakos.release="R${STEAKOS_RELEASE_MAJOR}.${STEAKOS_RELEASE_MINOR}" \
      steakos.version="V${STEAKOS_VERSION}"

ENV STEAKOS_PRODUCT_LINE=${STEAKOS_PRODUCT_LINE} \
    STEAKOS_RELEASE_MAJOR=${STEAKOS_RELEASE_MAJOR} \
    STEAKOS_RELEASE_MINOR=${STEAKOS_RELEASE_MINOR} \
    STEAKOS_BUILD_ID=${STEAKOS_BUILD_ID}

WORKDIR /app

# Expect a bootable WAR/JAR produced by Gradle (bootWar / bootJar)
COPY build/libs/*.war /app/app.war

EXPOSE 9090

ENTRYPOINT ["java", "-Dgrails.env=production", "-jar", "/app/app.war"]
