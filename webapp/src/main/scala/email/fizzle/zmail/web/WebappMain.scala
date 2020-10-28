package email.fizzle.zmail.web

import email.fizzle.service.GetMailBoxRequest
import email.fizzle.service.ZioService.MailBoxServiceClient
import scalapb.grpc.Channels
import scalapb.zio_grpc.ZManagedChannel
import org.scalajs.dom
import zio.{ App, ZIO }
import zio.console.{ Console, putStrLn }

object WebappMain extends App {
  val appLogic = for {
    _        <- putStrLn("Hello console 1")
    response <- MailBoxServiceClient.getMailBox(GetMailBoxRequest(username = "willem.vermeer"))
    _        <- putStrLn(response.toString)
    _ <- ZIO.effectTotal(dom.document.getElementById("header").innerHTML = response.username)
    _        <- putStrLn("Hello console 2")
  } yield ()

  val clientLayer = MailBoxServiceClient.live(
//    ZManagedChannel(Channels.grpcwebChannel("http://localhost:9000"))
    ZManagedChannel(Channels.grpcwebChannel(""))
  )
  val appLayer    = clientLayer ++ Console.live
//  val appLayer = Console.live

  def run(args: List[String]) =
    appLogic.provideLayer(appLayer).exitCode
}
