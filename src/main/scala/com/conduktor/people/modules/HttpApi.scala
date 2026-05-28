package com.conduktor.people.modules

import cats.effect.{Async, Resource}
import com.conduktor.people.http.routes.*
import org.http4s.HttpRoutes
import org.typelevel.log4cats.Logger
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter

class HttpApi[F[_]: Async: Logger] private (core: Core[F]):
  private val healthRoutes = HealthRoutes[F]
  private val personRoutes = PersonRoutes[F](core.persons)

  private val apiEndpoints: List[ServerEndpoint[Any, F]] =
    List(healthRoutes.serverEndpoint, personRoutes.serverEndpoint)

  private val swaggerEndpoints: List[ServerEndpoint[Any, F]] =
    SwaggerInterpreter().fromServerEndpoints[F](apiEndpoints, "conduktor people", "0.1.0-SNAPSHOT")

  val endpoints: HttpRoutes[F] =
    Http4sServerInterpreter[F]().toRoutes(apiEndpoints ++ swaggerEndpoints)

object HttpApi:
  def apply[F[_]: Async: Logger](core: Core[F]): Resource[F, HttpApi[F]] =
    Resource.pure(new HttpApi[F](core))
