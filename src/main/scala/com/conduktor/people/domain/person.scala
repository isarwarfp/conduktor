package com.conduktor.people.domain

import cats.MonadThrow
import cats.implicits.*
import cats.effect.Sync
import com.conduktor.people.domain.person.Person
import fs2.kafka.Deserializer
import io.circe.*
import io.circe.generic.semiauto.*
import io.circe.parser.decode

object person:
  case class Address(street: String, town: String, postcode: String)
  object Address:
    given addressDecoder: Decoder[Address] = deriveDecoder[Address]
    given addressEncoder: Encoder[Address] = deriveEncoder[Address]

  case class Person(
    _id: String,
    name: String,
    dob: String,
    address: Address,
    telephone: String,
    pets: List[String],
    score: Double,
    email: String,
    url: String,
    description: String,
    verified: Boolean,
    salary: Int
  )

  object Person:
    given personDecoder: Decoder[Person] = deriveDecoder[Person]
    given personEncoder: Encoder[Person] = deriveEncoder[Person]

object PersonKafka:
  given deserializer[F[_]: Sync]: Deserializer[F, Person] =
    Deserializer.string[F].flatMap { jsonString =>
      Deserializer.lift { _ =>
        decode[Person](jsonString) match {
          case Right(person) => person.pure[F]
          case Left(e) =>
            MonadThrow[F].raiseError(new RuntimeException(s"Failed to parse Person: ${e.getMessage}", e))
        }
      }
    }
