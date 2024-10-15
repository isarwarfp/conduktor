package com.conduktor.people.modules

import cats.effect.*
import com.conduktor.people.core.*
import com.conduktor.people.domain.KafkaContext
import fs2.kafka.KafkaConsumer
import org.typelevel.log4cats.Logger

final class Core[F[_]] private (val persons: Persons[F])

object Core:
  def apply[F[_]: MonadCancelThrow: Async: Logger](
    consumer: KafkaConsumer[F, String, String],
    kafkaCtx: KafkaContext
  ): Resource[F, Core[F]] =
    Resource.eval(LivePersons[F](consumer, kafkaCtx))
      .map( persons => new Core[F](persons) )