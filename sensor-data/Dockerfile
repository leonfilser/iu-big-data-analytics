FROM python:3.9-slim

RUN pip install --no-cache-dir kafka-python

COPY ${PWD}/random-sensor-data.py .

CMD ["python", "random-sensor-data.py"]