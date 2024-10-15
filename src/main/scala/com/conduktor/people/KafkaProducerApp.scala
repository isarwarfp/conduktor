package com.conduktor.people

import cats.effect.{IO, IOApp, Resource}
import fs2.kafka.{KafkaProducer, ProducerRecord, ProducerRecords, ProducerSettings}
import io.circe.{Json, parser}
import org.apache.kafka.clients.admin.{AdminClient, NewTopic}
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util.Properties
import scala.io.Source
import scala.jdk.CollectionConverters.*

object KafkaProducerApp extends IOApp.Simple {

  val logger = Slf4jLogger.getLogger[IO]

  def run: IO[Unit] = {

    val topicName = "people-topic"
    val bootstrapServers = "localhost:9093"

    val producerSettings = ProducerSettings[IO, String, String].withBootstrapServers(bootstrapServers)
    val adminProps = new Properties()
    adminProps.put("bootstrap.servers", bootstrapServers)
    val adminResource = Resource.make { IO(AdminClient.create(adminProps)) }(client => IO(client.close()))

    def createTopic(adminClient: AdminClient): IO[Unit] = IO {
      val newTopic = new NewTopic(topicName, 3, 1.toShort)
      val createResult = adminClient.createTopics(List(newTopic).asJava)
      createResult.all().get()
      ()
    }.handleErrorWith { error =>
      logger.error(s"Error creating topic: $error") *> IO.unit
    }

    def produceToAllPartitions(id: String, message: String, producer: KafkaProducer[IO, String, String]): IO[Unit] = {
      val partitions = List(0, 1, 2)
      fs2.Stream
        .emits(partitions)
        .evalMap { partition =>
          val producerRecord = ProducerRecord(topicName, id, message).withPartition(partition)
          logger.info(s"Producing message with _id $id to partition $partition: $message") *>
            producer.produce(ProducerRecords.one(producerRecord)).flatten
        }
        .compile
        .drain
    }

    def produceMessages: IO[Unit] = {
      val jsonData = Source.fromResource("random-people-data.json").mkString
      val parsedJson = parser.parse(jsonData) match {
        case Right(json) => IO.pure(json)
        case Left(error) => logger.error(s"Failed to parse JSON: $error") *>
          IO.pure(Json.Null)
      }

      parsedJson.flatMap { json =>
        json.hcursor.get[List[Json]]("ctRoot") match {
          case Right(people) =>
            KafkaProducer.resource(producerSettings).use { producer =>
              fs2.Stream
                .emits(people)
                .evalMap { personJson =>
                  val message = personJson.noSpaces
                  val id = personJson.hcursor.get[String]("_id").getOrElse("unknown")
                  produceToAllPartitions(id, message, producer)
                }
                .compile
                .drain
            }
          case Left(error) => logger.error(s"Failed to extract ctRoot from JSON: $error") *>
            IO.unit
        }
      }
    }

    adminResource.use { adminClient =>
      for {
        _ <- createTopic(adminClient)
        _ <- logger.info(s"Topic '$topicName' successfully created with 3 partitions")
        _ <- produceMessages
      } yield ()
    }
  }
}
