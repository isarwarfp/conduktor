package com.conduktor.people.http.routes

import cats.effect.*
import cats.implicits.*
import com.conduktor.people.core.Persons
import io.circe.Json
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.io.OptionalQueryParamDecoderMatcher
import org.typelevel.log4cats.Logger

class PersonRoutes[F[_]: Concurrent: Logger](persons: Persons[F]) extends Http4sDsl[F]:

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "persons" :? OffsetQueryParamMatcher(offset) +& CountQueryParamMatcher(count) =>
      for {
        records <- persons.getByOffsetAndCount(offset.getOrElse(0L), count.getOrElse(10))
        response <- Ok(Json.arr(records: _*))
      } yield response
  }

object PersonRoutes:
  def apply[F[_]: Concurrent: Logger](persons: Persons[F]): PersonRoutes[F] =
    new PersonRoutes[F](persons)

object OffsetQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Long]("offset")
object CountQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("count")

