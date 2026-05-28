# conduktor

A small Scala 3 / Cats Effect service that consumes `Person` records from a
Kafka topic and exposes them over a paged HTTP API with auto-generated
Swagger UI.

## Run it

Requires JDK 17+, sbt, and Docker.

```bash
docker compose up -d                                       # Kafka in KRaft mode (no Zookeeper)
sbt "runMain com.conduktor.people.KafkaProducerApp"        # seed the topic
sbt "runMain com.conduktor.people.Application"             # start the HTTP service on :4041
```

| URL                                                  | What                          |
|------------------------------------------------------|-------------------------------|
| http://localhost:4041/api/health                     | Liveness probe                |
| http://localhost:4041/api/persons?offset=0&count=10  | Paged records                 |
| http://localhost:4041/docs                           | Swagger UI                    |
| http://localhost:4041/docs/docs.yaml                 | OpenAPI 3 document            |

Invalid `count` (`[1, 1000]`) or `offset` out of `[begin, end)` returns
`400 {"error": "..."}`. Stop with `docker compose down`.

## Tests

```bash
sbt test       # unit — no Docker
sbt itTest     # integration against Testcontainers Kafka
```

## Approach

- **Tagless-final** end-to-end (`Persons[F]`, `KafkaContext[F]`,
  `PersonKafka.deserializer[F[_]: Sync]`); `Application` picks `IO` at the edge.
- **tapir** drives the http4s routes, the OpenAPI document, and Swagger UI from
  a single endpoint description. Input docs live on the annotated
  [`PersonsQuery`](src/main/scala/com/conduktor/people/http/PersonsQuery.scala)
  case class instead of inline DSL calls.
- **Typed errors at the service boundary.** `Persons[F]` returns
  `F[Either[PersonsError, List[Response]]]`; the HTTP layer maps `Left` to a
  `400`.
- **Orchestrator style.** Public methods are short for/`EitherT` pipelines that
  delegate to small focused helpers (see
  [`Persons.scala`](src/main/scala/com/conduktor/people/core/Persons.scala) and
  [`KafkaProducerApp.scala`](src/main/scala/com/conduktor/people/KafkaProducerApp.scala)).
- **Resource-safe.** Every client (admin, producer, consumer, classpath stream)
  is acquired via `Resource` / `Resource.fromAutoCloseable`.

## Design notes

- The producer **fans every record out to all partitions** so that one `offset`
  is well-defined across them — a deliberate simplification; a real system
  would key-hash and paginate per partition.
- Partitions are **discovered at startup** via `consumer.partitionsFor`.
- Topic name, partition count, and replication factor live in
  [`application.conf`](src/main/resources/application.conf) under
  `kafka-config.topic`.
- Consumer **auto-commit is off**: every request seeks to a caller-specified
  offset, so committing would be wrong.
