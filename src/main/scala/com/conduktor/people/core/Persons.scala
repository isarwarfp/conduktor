package com.conduktor.people.core

import cats.effect.*
import cats.implicits.*
import com.conduktor.people.domain.KafkaContext
import fs2.kafka.KafkaConsumer
import io.circe.*
import io.circe.parser.*
import org.typelevel.log4cats.Logger

trait Persons[F[_]]:
  def getByOffsetAndCount(offset: Long, count: Int): F[List[Json]]

final class LivePersons[F[_]: Async: Logger](
  consumer: KafkaConsumer[F, String, String],
  kafkaCtx: KafkaContext
) extends Persons[F]:

  override def getByOffsetAndCount(offset: Long, count: Int): F[List[Json]] =
    for {
      _ <- Logger[F].info(s"Assigning partitions and seeking to offset $offset")
      partitions = kafkaCtx.partitions
      isOffsetValid <- validOffset(kafkaCtx, consumer, offset)
      records <-
        if (!isOffsetValid)
          Logger[F].warn(s"Offset $offset is out of range for one or more partitions") *>
            List.empty[Json].pure[F]
        else
          consumer.assign(partitions) *>
            partitions.traverse_(tp => consumer.seek(tp, offset)) *>
            Logger[F].info(s"Successfully sought to offset $offset") *>
            consumer.stream
              .take(count.toLong)
              .evalTap { record => Logger[F].info(s"Processing record with offset ${record.offset}") }
              .map { committableRecord => parse(committableRecord.record.value).getOrElse(Json.Null) }
              .compile
              .toList
    } yield records

  private def validOffset(ctx: KafkaContext, consumer: KafkaConsumer[F, String, String], offset: Long) =
    for {
      _ <- Logger[F].info("Validating offset.")
      partitions = kafkaCtx.partitions
      beginningOffsets <- consumer.beginningOffsets(partitions.toSortedSet)
      endOffsets <- consumer.endOffsets(partitions.toSortedSet)
      isOffsetValid = partitions.toSortedSet.forall { tp =>
        val minOffset = beginningOffsets(tp)
        val maxOffset = endOffsets(tp)
        offset >= minOffset && offset < maxOffset
      }
    } yield isOffsetValid

object LivePersons:
  def apply[F[_] : Async : Logger](consumer: KafkaConsumer[F, String, String], kafkaCtx: KafkaContext): F[Persons[F]] =
    new LivePersons[F](consumer, kafkaCtx).pure[F]
