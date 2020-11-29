package zmail

import zio.ZIO
import zio.config._
import zio.config.magnolia.DeriveConfigDescriptor.descriptor
import zio.config.typesafe._

case class ZmailConfig(smtp: SmtpConfig, grpc: GrpcConfig, domain: String, tmpDir: String)
case class SmtpConfig(port: Int)
case class GrpcConfig(port: Int)

object Config {

  val live = loadConfig.orDie.toLayer

  private def loadConfig =
    for {
      source <- ZIO.fromEither(TypesafeConfigSource.fromDefaultLoader)
      config <- ZIO.fromEither(read(descriptor[ZmailConfig] from source))
    } yield config

}
