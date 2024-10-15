package com.conduktor.people.modules

import cats.*
import cats.effect.*
import cats.implicits.*
import com.conduktor.people.http.routes.*
import org.http4s.*
import org.http4s.server.*
import org.typelevel.log4cats.Logger

class HttpApi[F[_] : Concurrent : Logger] private(core: Core[F]):
  private val healthRoutes = HealthRoutes[F].routes
  private val personRoutes = PersonRoutes[F](core.persons).routes

  val endpoints: HttpRoutes[F] = Router(
    "/api" -> (healthRoutes <+> personRoutes)
  )

object HttpApi:
  def apply[F[_] : Concurrent : Logger](core: Core[F]): Resource[F, HttpApi[F]] =
    Resource.pure(new HttpApi[F](core))
