package com.conduktor.people.domain

import com.conduktor.people.domain.person.Person
import io.circe.*
import io.circe.generic.semiauto.*
import sttp.tapir.Schema.annotations.description

@description("A Person record paired with its Kafka coordinates.")
final case class Response(
    @description("The decoded Person payload.")
    person: Person,
    @description("Kafka partition the record was read from.")
    partition: Int,
    @description("Offset of the record within its partition.")
    offset: Long,
    @description("Kafka record key (the person's _id).")
    key: String
)

object Response:
  given personDecoder: Decoder[Response] = deriveDecoder[Response]
  given personEncoder: Encoder[Response] = deriveEncoder[Response]
