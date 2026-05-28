package com.conduktor.people.http

import cats.effect.IO
import com.conduktor.people.core.{Persons, PersonsError}
import com.conduktor.people.domain.Response
import com.conduktor.people.http.routes.{HealthRoutes, PersonRoutes}
import munit.CatsEffectSuite
import org.http4s.implicits.*
import org.http4s.{Method, Request, Status}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter

class SwaggerSpec extends CatsEffectSuite:

  private val noopPersons: Persons[IO] = new Persons[IO]:
    override def getByOffsetAndCount(offset: Long, count: Int): IO[Either[PersonsError, List[Response]]] =
      IO.pure(Right(List.empty))

  private val apiEndpoints: List[ServerEndpoint[Any, IO]] =
    List(HealthRoutes[IO].serverEndpoint, PersonRoutes[IO](noopPersons).serverEndpoint)

  private val swaggerEndpoints: List[ServerEndpoint[Any, IO]] =
    SwaggerInterpreter().fromServerEndpoints[IO](apiEndpoints, "conduktor people", "test")

  private val routes =
    Http4sServerInterpreter[IO]().toRoutes(apiEndpoints ++ swaggerEndpoints).orNotFound

  test("GET /docs/docs.yaml returns an OpenAPI document describing the API") {
    val req = Request[IO](Method.GET, uri"/docs/docs.yaml")
    for {
      resp <- routes.run(req)
      body <- resp.as[String]
    } yield {
      assertEquals(resp.status, Status.Ok)
      assert(body.contains("openapi:"), s"expected OpenAPI doc, got: ${body.take(120)}")
      assert(body.contains("/api/health"), "expected /api/health to appear in the spec")
      assert(body.contains("/api/persons"), "expected /api/persons to appear in the spec")
    }
  }

  test("OpenAPI document includes descriptions from annotations") {
    val req = Request[IO](Method.GET, uri"/docs/docs.yaml")
    routes.run(req).flatMap(_.as[String]).map { body =>
      assert(
        body.contains("Kafka offset to seek to on every partition"),
        "expected @description on PersonsQuery.offset to be rendered"
      )
      assert(
        body.contains("Number of records to return"),
        "expected @description on PersonsQuery.count to be rendered"
      )
      assert(
        body.contains("A Person record paired with its Kafka coordinates"),
        "expected class-level @description on Response to be rendered"
      )
      assert(
        body.contains("Error response body returned for 4xx responses"),
        "expected class-level @description on ErrorBody to be rendered"
      )
    }
  }
