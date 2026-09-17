FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build
COPY VehicleSimulator.java .
RUN javac --release 21 VehicleSimulator.java

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /build/*.class ./
USER 10001
ENTRYPOINT ["java", "VehicleSimulator"]
