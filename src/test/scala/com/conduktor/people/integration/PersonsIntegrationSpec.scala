package com.conduktor.people.integration

import cats.effect.IO
import cats.implicits.*
import com.conduktor.people.config.{KafkaConfig, KafkaConsumerConfig, KafkaProducerConfig, KafkaTopicConfig}
import com.conduktor.people.core.{LivePersons, Persons, PersonsError}
import com.conduktor.people.domain.KafkaContext
import com.conduktor.people.domain.person.{Address, Person}
import com.dimafeng.testcontainers.KafkaContainer
import com.dimafeng.testcontainers.munit.TestContainerForAll
import fs2.kafka.{KafkaConsumer, KafkaProducer, ProducerRecord, ProducerRecords, ProducerSettings}
import io.circe.syntax.*
import munit.CatsEffectSuite
import org.apache.kafka.clients.admin.{AdminClient, NewTopic}
import org.testcontainers.utility.DockerImageName
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util.{Properties, UUID}
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

class PersonsIntegrationSpec extends CatsEffectSuite with TestContainerForAll:

  override val munitTimeout: Duration = 3.minutes

  override val containerDef: KafkaContainer.Def =
    KafkaContainer.Def(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))

  private given Logger[IO] = Slf4jLogger.getLogger[IO]

  private val integrationTag = new munit.Tag("integration")
  override def munitTestTransforms: List[TestTransform] =
    super.munitTestTransforms :+ new TestTransform(
      "tag-as-integration",
      t => t.withTags(t.tags + integrationTag)
    )

  private val NumPartitions: Int = 3

  private def kafkaConfig(bootstrap: String, topic: String): KafkaConfig =
    KafkaConfig(
      bootstrapServers = bootstrap,
      topic            = KafkaTopicConfig(topic, NumPartitions, 1.toShort),
      producer         = KafkaProducerConfig("it-producer", bootstrap),
      consumer         = KafkaConsumerConfig(s"it-consumer-${UUID.randomUUID}", bootstrap)
    )

  private def samplePerson(id: String): Person =
    Person(
      _id = id,
      name = s"Person $id",
      dob = "2000-01-01",
      address = Address("1 A St", "Town", "AA1 1AA"),
      telephone = "+44-0",
      pets = Nil,
      score = 1.0,
      email = s"$id@example.com",
      url = "https://example.com",
      description = "",
      verified = true,
      salary = 100
    )

  private def createTopic(bootstrap: String, topic: String): IO[Unit] = IO {
    val props = new Properties()
    props.put("bootstrap.servers", bootstrap)
    val admin = AdminClient.create(props)
    try
      admin
        .createTopics(List(new NewTopic(topic, NumPartitions, 1.toShort)).asJava)
        .all()
        .get()
    finally admin.close()
    ()
  }

  private def produce(bootstrap: String, topic: String, ids: List[String]): IO[Unit] =
    val settings = ProducerSettings[IO, String, String].withBootstrapServers(bootstrap)
    KafkaProducer.resource(settings).use { producer =>
      ids.traverse_ { id =>
        val message = samplePerson(id).asJson.noSpaces
        (0 until NumPartitions).toList.traverse_ { partition =>
          val record = ProducerRecord(topic, id, message).withPartition(partition)
          producer.produce(ProducerRecords.one(record)).flatten.void
        }
      }
    }

  private def withPersons[A](bootstrap: String, topic: String)(body: Persons[IO] => IO[A]): IO[A] =
    val ctx = KafkaContext[IO](kafkaConfig(bootstrap, topic))
    KafkaConsumer.resource(ctx.consumerSettings).use { consumer =>
      LivePersons[IO](consumer, ctx).flatMap(body)
    }

  test("getByOffsetAndCount returns records produced to Kafka") {
    withContainers { kafka =>
      val topic = s"it-success-${UUID.randomUUID}"
      val ids   = List("a", "b", "c", "d", "e")
      val program = for {
        _      <- createTopic(kafka.bootstrapServers, topic)
        _      <- produce(kafka.bootstrapServers, topic, ids)
        result <- withPersons(kafka.bootstrapServers, topic)(_.getByOffsetAndCount(0L, 3))
      } yield result

      program.map {
        case Right(records) =>
          assertEquals(records.size, 3)
          records.foreach(r => assert(ids.contains(r.key), s"unexpected key ${r.key}"))
        case Left(err) => fail(s"expected records, got: $err")
      }
    }
  }

  test("getByOffsetAndCount returns OffsetOutOfRange when offset exceeds the end") {
    withContainers { kafka =>
      val topic = s"it-out-of-range-${UUID.randomUUID}"
      val program = for {
        _      <- createTopic(kafka.bootstrapServers, topic)
        _      <- produce(kafka.bootstrapServers, topic, List("a", "b"))
        result <- withPersons(kafka.bootstrapServers, topic)(_.getByOffsetAndCount(9999L, 5))
      } yield result

      program.map {
        case Left(PersonsError.OffsetOutOfRange(o, _, _)) => assertEquals(o, 9999L)
        case other                                        => fail(s"expected OffsetOutOfRange, got: $other")
      }
    }
  }

  test("getByOffsetAndCount rejects an out-of-bound count with InvalidCount") {
    withContainers { kafka =>
      val topic = s"it-invalid-count-${UUID.randomUUID}"
      val program = for {
        _      <- createTopic(kafka.bootstrapServers, topic)
        _      <- produce(kafka.bootstrapServers, topic, List("a"))
        result <- withPersons(kafka.bootstrapServers, topic)(_.getByOffsetAndCount(0L, -1))
      } yield result

      program.map {
        case Left(PersonsError.InvalidCount(provided, _, _)) => assertEquals(provided, -1)
        case other                                           => fail(s"expected InvalidCount, got: $other")
      }
    }
  }
