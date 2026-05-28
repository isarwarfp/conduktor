package com.conduktor.people

import cats.effect.{IO, IOApp, Resource}
import cats.implicits.*
import com.conduktor.people.config.{AppConfig, KafkaConfig, KafkaTopicConfig}
import com.conduktor.people.config.syntax.*
import fs2.kafka.{KafkaProducer, ProducerRecord, ProducerRecords, ProducerSettings}
import io.circe.{Json, parser}
import org.apache.kafka.clients.admin.{AdminClient, AdminClientConfig, NewTopic}
import org.apache.kafka.common.errors.TopicExistsException
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import pureconfig.ConfigSource

import java.util.Properties
import java.util.concurrent.ExecutionException
import scala.io.Source
import scala.jdk.CollectionConverters.*

object KafkaProducerApp extends IOApp.Simple:

  private val DataResource: String = "random-people-data.json"
  private val RootField: String    = "ctRoot"
  private val IdField: String      = "_id"

  private given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  override def run: IO[Unit] =
    for {
      appConfig <- ConfigSource.default.loadF[IO, AppConfig]
      kafkaCfg   = appConfig.kafkaConfig
      _         <- ensureTopic(kafkaCfg)
      people    <- loadPeople
      _         <- publish(kafkaCfg, people)
    } yield ()

  private def ensureTopic(cfg: KafkaConfig): IO[Unit] =
    adminClient(cfg.bootstrapServers)
      .use(createTopic(_, cfg.topic))
      .recoverWith {
        case e: ExecutionException if e.getCause.isInstanceOf[TopicExistsException] =>
          Logger[IO].info(s"Topic '${cfg.topic.name}' already exists — skipping creation")
      } *> Logger[IO].info(s"Topic '${cfg.topic.name}' is ready (${cfg.topic.numPartitions} partitions)")

  private def adminClient(bootstrapServers: String): Resource[IO, AdminClient] =
    Resource.fromAutoCloseable(IO.blocking(AdminClient.create(adminProperties(bootstrapServers))))

  private def adminProperties(bootstrapServers: String): Properties =
    val props = new Properties()
    props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
    props

  private def createTopic(admin: AdminClient, topic: KafkaTopicConfig): IO[Unit] =
    IO.blocking {
      admin
        .createTopics(List(new NewTopic(topic.name, topic.numPartitions, topic.replicationFactor)).asJava)
        .all()
        .get()
    }.void

  private def loadPeople: IO[List[Json]] =
    for {
      raw    <- readClasspathResource(DataResource)
      json   <- IO.fromEither(parser.parse(raw))
      people <- IO.fromEither(json.hcursor.get[List[Json]](RootField))
      _      <- Logger[IO].info(s"Loaded ${people.size} people from '$DataResource'")
    } yield people

  private def readClasspathResource(name: String): IO[String] =
    Resource
      .fromAutoCloseable(IO.blocking(Source.fromResource(name)))
      .use(src => IO.blocking(src.mkString))

  private def publish(cfg: KafkaConfig, people: List[Json]): IO[Unit] =
    val records = people.flatMap(toRecords(cfg.topic, _))
    KafkaProducer.resource(producerSettings(cfg)).use { producer =>
      Logger[IO].info(s"Publishing ${records.size} records to '${cfg.topic.name}'") *>
        producer.produce(ProducerRecords(records)).flatten.void *>
        Logger[IO].info(s"Published ${records.size} records successfully")
    }

  private def producerSettings(cfg: KafkaConfig): ProducerSettings[IO, String, String] =
    ProducerSettings[IO, String, String]
      .withBootstrapServers(cfg.producer.bootstrapServers)
      .withClientId(cfg.producer.clientId)

  private def toRecords(topic: KafkaTopicConfig, person: Json): List[ProducerRecord[String, String]] =
    val id      = person.hcursor.get[String](IdField).getOrElse("unknown")
    val message = person.noSpaces
    (0 until topic.numPartitions).toList.map { partition =>
      ProducerRecord(topic.name, id, message).withPartition(partition)
    }
