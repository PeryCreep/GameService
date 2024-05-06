package configuration

import pureconfig.ConfigSource
import pureconfig.generic.auto._


case class ServerConf(host: String, port: Int, jwtKey: String)

object ServerConf {
  lazy val instance: ServerConf = ConfigSource.default.at("server").loadOrThrow[ServerConf]
}
