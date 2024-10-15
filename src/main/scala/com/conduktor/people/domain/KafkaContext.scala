package com.conduktor.people.domain

import cats.data.NonEmptySet
import cats.effect.*
import cats.implicits.*
import com.conduktor.people.config.KafkaConfig
import fs2.kafka.ConsumerSettings
import fs2.kafka.instances.fs2KafkaTopicPartitionOrder
import org.apache.kafka.common.TopicPartition

final case class KafkaContext(
    partitions: NonEmptySet[TopicPartition],
    consumerSettings: ConsumerSettings[IO, String, String],
    kafkaConfig: KafkaConfig
)
object KafkaContext:
  def apply(kafkaConfig: KafkaConfig): KafkaContext =
    val consumerSettings = ConsumerSettings[IO, String, String]
      .withBootstrapServers(kafkaConfig.bootstrapServers)
      .withGroupId(kafkaConfig.consumer.groupId)
      .withAutoOffsetReset(fs2.kafka.AutoOffsetReset.Earliest)
      .withEnableAutoCommit(false)

    val partitions = NonEmptySet.of(
      new TopicPartition(kafkaConfig.topic, 0),
      new TopicPartition(kafkaConfig.topic, 1),
      new TopicPartition(kafkaConfig.topic, 2)
    )

    KafkaContext(partitions, consumerSettings, kafkaConfig)

