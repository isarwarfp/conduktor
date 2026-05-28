package com.conduktor.people.http.routes

import cats.Applicative
import cats.implicits.*
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint

class HealthRoutes[F[_]: Applicative]:
  val endpoint: PublicEndpoint[Unit, Unit, String, Any] =
    sttp.tapir.endpoint.get
      .in("api" / "health")
      .out(stringBody)
      .summary("Liveness probe")
      .tag("Health")

  val serverEndpoint: ServerEndpoint[Any, F] =
    endpoint.serverLogicSuccess(_ => "Working !".pure[F])

object HealthRoutes:
  def apply[F[_]: Applicative]: HealthRoutes[F] = new HealthRoutes[F]
