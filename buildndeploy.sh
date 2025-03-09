set -e

# Erstellt die .jar-Datei für Apache Flink mit einem temporären Maven Build-Container
docker run --rm \
    -v ~/.m2:/root/.m2 \
    -v ./maven:/usr/src/maven \
    -w /usr/src/maven \
    maven:3-openjdk-18-slim \
    mvn clean package

# Startet und rebuildet die Docker Container
docker compose up -d --build

# Warten bis die Container vollständig gestartet sind
sleep 60

# Kopiert die zuvor erstellte .jar-Datei in den Flink-Jobmanager-Container
docker cp maven/target/sensor-data-1.0.jar flink-jobmanager:/

# Startet den Flink-Job innerhalb des Jobmanager-Containers
docker exec -it flink-jobmanager flink run -d -c SensorDataPipeline /sensor-data-1.0.jar