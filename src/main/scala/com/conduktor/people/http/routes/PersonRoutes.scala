package com.conduktor.people.http.routes

import cats.Functor
import cats.syntax.functor.*
import com.conduktor.people.core.Persons
import com.conduktor.people.domain.Response
import com.conduktor.people.http.{ErrorBody, PersonsQuery}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint

class PersonRoutes[F[_]: Functor](persons: Persons[F]):
  val endpoint: PublicEndpoint[PersonsQuery, ErrorBody, List[Response], Any] =
    sttp.tapir.endpoint.get
      .in("api" / "persons")
      .in(EndpointInput.derived[PersonsQuery])
      .out(jsonBody[List[Response]])
      .errorOut(statusCode(StatusCode.BadRequest).and(jsonBody[ErrorBody]))
      .summary("Page through Person records by Kafka offset")
      .tag("Persons")

  val serverEndpoint: ServerEndpoint[Any, F] = endpoint.serverLogic { q =>
    persons.getByOffsetAndCount(q.offset.getOrElse(0L), q.count.getOrElse(10)).map {
      case Right(records) => Right(records)
      case Left(err)      => Left(ErrorBody(err.message))
    }
  }

object PersonRoutes:
  def apply[F[_]: Functor](persons: Persons[F]): PersonRoutes[F] =
    new PersonRoutes[F](persons)
