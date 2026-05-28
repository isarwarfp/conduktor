package com.conduktor.people.http.routes

import cats.effect.IO
import munit.CatsEffectSuite
import org.http4s.implicits.*
import org.http4s.{Method, Request, Status}
import sttp.tapir.server.http4s.Http4sServerInterpreter

class HealthRoutesSpec extends CatsEffectSuite:

  private val routes =
    Http4sServerInterpreter[IO]().toRoutes(HealthRoutes[IO].serverEndpoint).orNotFound

  test("GET /api/health responds 200 with 'Working !'") {
    val req = Request[IO](Method.GET, uri"/api/health")
    for {
      resp <- routes.run(req)
      body <- resp.as[String]
    } yield {
      assertEquals(resp.status, Status.Ok)
      assertEquals(body, "Working !")
    }
  }

  test("GET /unknown returns 404") {
    val req = Request[IO](Method.GET, uri"/unknown")
    routes.run(req).map(r => assertEquals(r.status, Status.NotFound))
  }
