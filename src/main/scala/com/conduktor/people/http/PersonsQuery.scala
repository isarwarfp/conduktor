package com.conduktor.people.http

import sttp.tapir.EndpointIO.annotations.{description, query}

final case class PersonsQuery(
    @query
    @description("Kafka offset to seek to on every partition. Defaults to 0.")
    offset: Option[Long],
    @query
    @description("Number of records to return (1..1000). Defaults to 10.")
    count: Option[Int]
)
