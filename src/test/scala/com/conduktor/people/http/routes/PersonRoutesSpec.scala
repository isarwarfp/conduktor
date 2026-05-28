package com.conduktor.people.http.routes

import cats.effect.{IO, Ref}
import com.conduktor.people.core.{LivePersons, Persons, PersonsError}
import com.conduktor.people.domain.Response
import com.conduktor.people.domain.person.{Address, Person}
import com.conduktor.people.http.ErrorBody
import io.circe.Json
import munit.CatsEffectSuite
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.implicits.*
import org.http4s.{Method, Request, Status}
import sttp.tapir.server.http4s.Http4sServerInterpreter

class PersonRoutesSpec extends CatsEffectSuite:

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

  private def stub(result: Either[PersonsError, List[Response]]): Persons[IO] = new Persons[IO]:
    override def getByOffsetAndCount(offset: Long, count: Int): IO[Either[PersonsError, List[Response]]] =
      IO.pure(result)

  private def routesWith(stubPersons: Persons[IO]) =
    Http4sServerInterpreter[IO]().toRoutes(PersonRoutes[IO](stubPersons).serverEndpoint).orNotFound

  test("GET /api/persons returns the records from the service") {
    val records = List(Response(samplePerson, partition = 0, offset = 0L, key = "ID-1"))
    val routes = routesWith(stub(Right(records)))

    val req = Request[IO](Method.GET, uri"/api/persons?offset=0&count=10")
    for {
      resp <- routes.run(req)
      body <- resp.as[List[Response]]
    } yield {
      assertEquals(resp.status, Status.Ok)
      assertEquals(body.map(_.key), List("ID-1"))
    }
  }

  test("GET /api/persons forwards default offset=0 and count=10 when params are missing") {
    Ref.of[IO, Option[(Long, Int)]](None).flatMap { captured =>
      val persons = new Persons[IO]:
        override def getByOffsetAndCount(offset: Long, count: Int): IO[Either[PersonsError, List[Response]]] =
          captured.set(Some((offset, count))).as(Right(List.empty))

      val routes = Http4sServerInterpreter[IO]().toRoutes(PersonRoutes[IO](persons).serverEndpoint).orNotFound
      val req    = Request[IO](Method.GET, uri"/api/persons")
      for {
        _    <- routes.run(req)
        args <- captured.get
      } yield assertEquals(args, Some((0L, 10)))
    }
  }

  test("GET /api/persons returns 400 when service reports InvalidCount") {
    val err = PersonsError.InvalidCount(provided = 0, min = LivePersons.MinCount, max = LivePersons.MaxCount)
    val routes = routesWith(stub(Left(err)))

    val req = Request[IO](Method.GET, uri"/api/persons?count=0")
    for {
      resp <- routes.run(req)
      body <- resp.as[ErrorBody]
    } yield {
      assertEquals(resp.status, Status.BadRequest)
      assertEquals(body.error, err.message)
    }
  }

  test("GET /api/persons returns 400 when service reports OffsetOutOfRange") {
    val err = PersonsError.OffsetOutOfRange(offset = 999, minOffset = 0, maxOffset = 100)
    val routes = routesWith(stub(Left(err)))

    val req = Request[IO](Method.GET, uri"/api/persons?offset=999")
    for {
      resp <- routes.run(req)
      body <- resp.as[ErrorBody]
    } yield {
      assertEquals(resp.status, Status.BadRequest)
      assertEquals(body.error, err.message)
    }
  }
