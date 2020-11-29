package zmail

import zio.blocking.Blocking
import zio.clock.Clock
import zio.config.{ getConfig, ZConfig }
import zio.console.{ putStrLn, Console }
import zio.{ App, Layer, ZManaged }
import zmail.grpc.GrpcServer
import zmail.smtp.SmtpServer

object Main extends App {

  val zioServices                                  = Clock.live ++ Blocking.live ++ Console.live
  val config: Layer[Nothing, ZConfig[ZmailConfig]] = Config.live
  val messageStore                                 = MessageStore.live
  val grpcService                                  = (Console.live ++ messageStore) >>> GrpcServer.service
  val appEnv                                       = grpcService ++ messageStore ++ zioServices ++ config

  val mainLogic =
    for {
      config <- getConfig[ZmailConfig].toManaged_
      _      <- SmtpServer.smtpServer
      _      <- GrpcServer.server(config.grpc.port)
      _      <- ZManaged.fromEffect(putStrLn("Zmail has started - both smtp and grpc services are running"))
    } yield ()

  override def run(args: List[String]) =
    mainLogic.useForever.provideLayer(appEnv).exitCode

}
