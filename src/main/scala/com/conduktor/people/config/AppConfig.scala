package com.conduktor.people.config

import pureconfig.ConfigReader
import pureconfig.generic.derivation.default.*

final case class AppConfig(
  kafkaConfig: KafkaConfig,
  emberConfig: EmberConfig
) derives ConfigReader
