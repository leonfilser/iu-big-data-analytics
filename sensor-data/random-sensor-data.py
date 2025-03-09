import json
import time
import random
from datetime import datetime
from kafka import KafkaProducer

###
# Das Skript simuliert Werte von 4 Temperatursensoren und sendet diese jede Sekunde an einen Apache Kafka Container zur weiteren Verarbeitung
###

# Verzögert den Start des Skript damit der Kafka Container Zeit zum Starten hat
time.sleep(30)

# Kafka-Container und Topic
bootstrap_server = ['kafka:9092']
topic = 'sensor-data'

# Kafka-Producer initialisieren (JSON-Serialisierung)
producer = KafkaProducer(
    bootstrap_servers=bootstrap_server,
    value_serializer=lambda v: json.dumps(v).encode('utf-8')
)

# Initialisierung von 4 Sensoren mit einer Starttemperatur zwischen 90 und 110°C
sensors = {f"s_{i + 1}": random.randint(90, 110) for i in range(4)}

# Stellt sicher, dass die Temperaturänderung pro Zeitintervall nicht zufällig, sondern halbwegs realistisch ist
random_change = [-1, -0.5, 0, 0.5, 1]

###

try:
    while True:
        for sensor in sensors:

            # Ändert die Temperatur zufällig, um einen annähernd realistischen Verlauf darzustellen
            # Sorgt zudem dafür, dass die Temperatur nur zischen 20 und 200°C liegen kann
            sensors[sensor] = max(20, min(200, sensors[sensor] + random.choice(random_change)))

            
            # Erstellen der Kafka Nachricht mit Zeitstempel
            data = {
                "sensor": sensor,
                "timestamp": datetime.utcnow().isoformat() + "Z",
                "temperature": sensors[sensor]
            }
                
            # Senden der Nachricht an Kafka
            producer.send(topic, value=data)
            
        # 1S warten
        time.sleep(1)

finally:
    producer.flush()
    producer.close()