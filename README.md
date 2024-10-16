# conduktor

## Steps
1. To setup environment `docker compose up -d`
2. Run `KafkaProducerApp` that would publish `500` messages to each partition
3. Run `Application` and that would start consumer and make available `http` endpoints
4. Call 
    ```bash
    curl "http://localhost:4041/api/persons?offset=0&count=10" 
    ```