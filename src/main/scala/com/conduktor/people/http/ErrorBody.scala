package com.conduktor.people.http

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.description

@description("Error response body returned for 4xx responses.")
final case class ErrorBody(
    @description("Human-readable explanation of what went wrong.")
    error: String
)

object ErrorBody:
  given decoder: Decoder[ErrorBody] = deriveDecoder
  given encoder: Encoder[ErrorBody] = deriveEncoder
  given schema: Schema[ErrorBody] = Schema.derived
