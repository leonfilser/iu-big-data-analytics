import java.util.Properties;

import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.functions.MapFunction;

import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;



public class SensorDataPipeline {

    // Kafka-Konstanten
    private static final String KAFKA_BOOTSTRAP_SERVER = "kafka:9092";
    private static final String KAFKA_TOPIC = "sensor-data";
    private static final String KAFKA_GROUP_ID = "sensor-data-group";

    // Influx-Konstanten
    private static final String INFLUXDB_URL = "http://influxdb:8086";
    private static final String INFLUXDB_TOKEN = "my-token";
    private static final String INFLUXDB_ORG = "my-org";
    private static final String INFLUXDB_BUCKET = "my-bucket";

    public static void main(String[] args) throws Exception {

        // Erstellt eine Flink-Stream-Umgebung
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Kafka Consumer Eigenschaften setzen
        Properties kafkaProps = new Properties();
        kafkaProps.setProperty("bootstrap.servers", KAFKA_BOOTSTRAP_SERVER);
        kafkaProps.setProperty("group.id", KAFKA_GROUP_ID);

        // Kafka Source initialisieren
        FlinkKafkaConsumer<String> kafkaConsumer = new FlinkKafkaConsumer<>(
                KAFKA_TOPIC,
                new SimpleStringSchema(),
                kafkaProps
        );

        // Stream aus Kafka lesen
        DataStream<String> sensorStream = env.addSource(kafkaConsumer);

        // JSON-Parsing und Mapping zu SensorData-Objekten
        DataStream<SensorData> parsedStream = sensorStream.map(new SensorDataMapper());

        // Sink für InfluxDB hinzufügen
        parsedStream.addSink(new InfluxDBSink());

        // Flink-Job starten
        env.execute("Sensor Data Flink Pipeline");
    }

    /**
     * Klasse für Sensordatenmessung.
     */
    public static class SensorData {
        public String sensor;
        public String timestamp;
        public double temperature;

        public SensorData(String sensor, String timestamp, double temperature) {
            this.sensor = sensor;
            this.timestamp = timestamp;
            this.temperature = temperature;
        }
    }

    /**
     * Konvertieren von JSON-Strings in SensorData-Objekte.
     */
    public static class SensorDataMapper implements MapFunction<String, SensorData> {
        private static final ObjectMapper objectMapper = new ObjectMapper(); // Singleton für Effizienz

        @Override
        public SensorData map(String value) throws Exception {
            JsonNode jsonNode = objectMapper.readTree(value);
            String sensor = jsonNode.get("sensor").asText();
            String timestamp = jsonNode.get("timestamp").asText();
            double temperature = jsonNode.get("temperature").asDouble();
            return new SensorData(sensor, timestamp, temperature);
        }
    }

    /**
     * Flink-Sink zum Schreiben von Sensordaten in InfluxDB.
     */
    public static class InfluxDBSink extends RichSinkFunction<SensorData> {
        private transient InfluxDBClient client; // Transiente Variable, damit sie nicht serialisiert wird

        @Override
        public void open(org.apache.flink.configuration.Configuration parameters) {
            // InfluxDB-Client einmalig initialisieren
            client = InfluxDBClientFactory.create(INFLUXDB_URL, INFLUXDB_TOKEN.toCharArray(), INFLUXDB_ORG, INFLUXDB_BUCKET);
        }

        @Override
        public void invoke(SensorData sensor, Context context) {
            // Aktuellen Zeitstempel in Millisekunden abrufen
            long timestampMillis = System.currentTimeMillis();
            
            // Datenpunkt für InfluxDB erstellen
            Point point = Point.measurement("sensor-data")
                    .addTag("sensor", sensor.sensor)
                    .addField("temperature", sensor.temperature)
                    .time(timestampMillis, WritePrecision.MS);

            // Daten in InfluxDB schreiben
            client.getWriteApiBlocking().writePoint(point);
        }

        @Override
        public void close() {
            // InfluxDB-Client schließen, um Ressourcen freizugeben
            if (client != null) {
                client.close();
            }
        }
    }
}