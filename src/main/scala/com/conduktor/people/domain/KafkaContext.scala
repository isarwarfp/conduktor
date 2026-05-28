package com.conduktor.people.domain

import cats.effect.Sync
import com.conduktor.people.config.KafkaConfig
import com.conduktor.people.domain.PersonKafka.given
import com.conduktor.people.domain.person.Person
import fs2.kafka.ConsumerSettings

final case class KafkaContext[F[_]](
    consumerSettings: ConsumerSettings[F, String, Person],
    kafkaConfig: KafkaConfig
)

object KafkaContext:
  def apply[F[_]: Sync](kafkaConfig: KafkaConfig): KafkaContext[F] =
    val consumerSettings = ConsumerSettings[F, String, Person]
      .withBootstrapServers(kafkaConfig.bootstrapServers)
      .withGroupId(kafkaConfig.consumer.groupId)
      .withAutoOffsetReset(fs2.kafka.AutoOffsetReset.Earliest)
      .withEnableAutoCommit(false)

    KafkaContext(consumerSettings, kafkaConfig)
