package com.conduktor.people.domain

import io.circe.*
import io.circe.generic.semiauto.*

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
