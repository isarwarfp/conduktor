package com.conduktor.people.config

import pureconfig.ConfigReader
import pureconfig.generic.derivation.default.*

final case class KafkaProducerConfig(clientId: String, bootstrapServers: String) derives ConfigReader
final case class KafkaConsumerConfig(groupId: String, bootstrapServers: String) derives ConfigReader

final case class KafkaTopicConfig(
  name: String,
  numPartitions: Int,
  replicationFactor: Short
) derives ConfigReader

final case class KafkaConfig(
  bootstrapServers: String,
  topic: KafkaTopicConfig,
  producer: KafkaProducerConfig,
  consumer: KafkaConsumerConfig
) derives ConfigReader
