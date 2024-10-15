package com.conduktor.people

import cats.*
import cats.effect.{IO, IOApp}
import com.conduktor.people.config.AppConfig
import com.conduktor.people.config.syntax.*
import com.conduktor.people.domain.KafkaContext
import com.conduktor.people.modules.{Core, HttpApi}
import fs2.kafka.*
import org.http4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import pureconfig.ConfigSource

object Application extends IOApp.Simple:
  given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  override def run: IO[Unit] = ConfigSource.default.loadF[IO, AppConfig].flatMap {
    case AppConfig(kafkaConfig, emberConfig) =>
      val kafkaCtx = KafkaContext(kafkaConfig)
      val appResource = for {
        consumer <- KafkaConsumer.resource(kafkaCtx.consumerSettings)
        core <- Core[IO](consumer, kafkaCtx)
        api <- HttpApi[IO](core)
        server <- EmberServerBuilder
          .default[IO]
          .withHost(emberConfig.host)
          .withPort(emberConfig.port)
          .withHttpApp(api.endpoints.orNotFound)
          .build
      } yield server

      appResource.use { _ =>
        logger.info("Server Started.") *> IO.never
      }
  }

