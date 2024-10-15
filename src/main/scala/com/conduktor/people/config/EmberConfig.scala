package com.conduktor.people.config

import com.comcast.ip4s.{Host, Port}
import pureconfig.ConfigReader
import pureconfig.error.CannotConvert
import pureconfig.generic.derivation.default.*

// given configReader: ConfigReader[EmberConfig]
final case class EmberConfig(host: Host, port: Port) derives ConfigReader

object EmberConfig:
  given hostReader: ConfigReader[Host] = ConfigReader[String].emap { hostStr =>
    Host.fromString(hostStr) match
      case None => Left(CannotConvert(hostStr, Host.getClass.toString, s"Invalid host $hostStr"))
      case Some(host) => Right(host)
  }

  given porReader: ConfigReader[Port] = ConfigReader[Int].emap { portInt =>
    Port
      .fromInt(portInt)
      .toRight(CannotConvert(portInt.toString, Port.getClass.toString, s"Invalid port $portInt"))
  }

