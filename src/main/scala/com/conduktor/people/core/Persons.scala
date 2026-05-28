package com.conduktor.people.core

import cats.data.{EitherT, NonEmptyList, NonEmptySet}
import cats.effect.*
import cats.implicits.*
import com.conduktor.people.domain.person.Person
import com.conduktor.people.domain.{KafkaContext, Response}
import fs2.kafka.KafkaConsumer
import fs2.kafka.instances.fs2KafkaTopicPartitionOrder
import org.apache.kafka.common.TopicPartition
import org.typelevel.log4cats.Logger

sealed trait PersonsError extends Product with Serializable:
  def message: String

object PersonsError:
  final case class InvalidCount(provided: Int, min: Int, max: Int) extends PersonsError:
    val message: String = s"count must be between $min and $max (got $provided)"

  final case class OffsetOutOfRange(offset: Long, minOffset: Long, maxOffset: Long) extends PersonsError:
    val message: String = s"offset $offset is out of range [$minOffset, $maxOffset)"

trait Persons[F[_]]:
  def getByOffsetAndCount(offset: Long, count: Int): F[Either[PersonsError, List[Response]]]

final class LivePersons[F[_]: Async: Logger] private (
    consumer: KafkaConsumer[F, String, Person],
    partitions: NonEmptySet[TopicPartition]
) extends Persons[F]:

  import LivePersons.{MaxCount, MinCount}

  override def getByOffsetAndCount(offset: Long, count: Int): F[Either[PersonsError, List[Response]]] =
    (for {
      _       <- EitherT.fromEither[F](validateCount(count))
      _       <- EitherT(validateOffsetInRange(offset))
      _       <- EitherT.liftF[F, PersonsError, Unit](seekAllPartitions(offset))
      records <- EitherT.liftF[F, PersonsError, List[Response]](streamRecords(count))
    } yield records).value

  private def validateCount(count: Int): Either[PersonsError, Unit] =
    Either.cond(
      count >= MinCount && count <= MaxCount,
      (),
      PersonsError.InvalidCount(count, MinCount, MaxCount)
    )

  private def validateOffsetInRange(offset: Long): F[Either[PersonsError, Unit]] =
    for {
      beginnings <- consumer.beginningOffsets(partitions.toSortedSet)
      ends       <- consumer.endOffsets(partitions.toSortedSet)
      outOfRange = partitions.toSortedSet.collectFirst {
        case tp if offset < beginnings(tp) || offset >= ends(tp) =>
          PersonsError.OffsetOutOfRange(offset, beginnings(tp), ends(tp))
      }
      _ <- outOfRange.traverse_(err => Logger[F].warn(err.message))
    } yield outOfRange.toLeft(())

  private def seekAllPartitions(offset: Long): F[Unit] =
    Logger[F].info(s"Assigning ${partitions.length} partitions and seeking to offset $offset") *>
      consumer.assign(partitions) *>
      partitions.traverse_(consumer.seek(_, offset)) *>
      Logger[F].info(s"Successfully sought to offset $offset")

  private def streamRecords(count: Int): F[List[Response]] =
    consumer.stream
      .take(count.toLong)
      .map(_.record)
      .evalTap(r => Logger[F].info(s"Processing record at partition ${r.partition}, offset ${r.offset}"))
      .map(toResponse)
      .compile
      .toList

  private def toResponse(record: fs2.kafka.ConsumerRecord[String, Person]): Response =
    val person = record.value
    Response(person, record.partition, record.offset, person._id)

object LivePersons:
  val MinCount: Int = 1
  val MaxCount: Int = 1000

  def apply[F[_]: Async: Logger](
      consumer: KafkaConsumer[F, String, Person],
      kafkaCtx: KafkaContext[F]
  ): F[Persons[F]] =
    val topic = kafkaCtx.kafkaConfig.topic.name
    for {
      partitionInfos <- consumer.partitionsFor(topic)
      _              <- Logger[F].info(s"Discovered ${partitionInfos.size} partitions for topic '$topic'")
      tps             = partitionInfos.map(pi => new TopicPartition(topic, pi.partition))
      partitions <- Async[F].fromOption(
        NonEmptyList.fromList(tps).map(_.toNes),
        new RuntimeException(s"No partitions found for topic '$topic'")
      )
    } yield new LivePersons[F](consumer, partitions)
