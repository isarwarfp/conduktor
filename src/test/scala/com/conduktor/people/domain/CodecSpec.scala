package com.conduktor.people.domain

import cats.effect.IO
import com.conduktor.people.domain.person.{Address, Person}
import com.conduktor.people.domain.PersonKafka.given
import fs2.kafka.Deserializer
import io.circe.parser.parse
import io.circe.syntax.*
import munit.CatsEffectSuite
import fs2.kafka.Headers

class CodecSpec extends CatsEffectSuite:

  private val samplePerson = Person(
    _id = "ID-1",
    name = "Ada Lovelace",
    dob = "1815-12-10",
    address = Address("1 Analytical Way", "London", "W1A 1AA"),
    telephone = "+44-0000-000-000",
    pets = List("Cat"),
    score = 9.9,
    email = "ada@example.com",
    url = "https://example.com",
    description = "math",
    verified = true,
    salary = 100
  )

  test("Person round-trips through JSON") {
    val decoded = samplePerson.asJson.as[Person]
    assertEquals(decoded, Right(samplePerson))
  }

  test("Response round-trips through JSON") {
    val response = Response(samplePerson, partition = 2, offset = 42L, key = "ID-1")
    val decoded  = response.asJson.as[Response]
    assertEquals(decoded, Right(response))
  }

  test("Person decoder fails when required fields are missing") {
    val json = parse("""{"_id":"only-an-id"}""").toOption.get
    assert(json.as[Person].isLeft)
  }

  test("PersonKafka deserializer parses valid JSON bytes into a Person") {
    val bytes = samplePerson.asJson.noSpaces.getBytes("UTF-8")
    val des   = summon[Deserializer[IO, Person]]
    des.deserialize("people-topic", Headers.empty, bytes).map { decoded =>
      assertEquals(decoded, samplePerson)
    }
  }

  test("PersonKafka deserializer raises an error on invalid JSON") {
    val bytes = "not a json document".getBytes("UTF-8")
    val des   = summon[Deserializer[IO, Person]]
    des.deserialize("people-topic", Headers.empty, bytes).attempt.map {
      case Left(e)  => assert(e.getMessage.contains("Failed to parse Person"))
      case Right(_) => fail("expected deserialization to fail")
    }
  }
