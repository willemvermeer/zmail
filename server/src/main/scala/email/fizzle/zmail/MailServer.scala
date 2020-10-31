package email.fizzle.zmail

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Paths

import email.fizzle.service.ZioService._
import email.fizzle.service.{ GetMailBoxRequest, GetMessageRequest, MailBox, MailBoxEntry }
import email.fizzle.zmail.MessageStore.MessageStoreT
import io.grpc.{ ServerBuilder, Status }
import io.grpc.protobuf.services.ProtoReflectionService
import scalapb.zio_grpc.{ RequestContext, Server, ServerLayer, ServerMain, ServiceList }
import zio.blocking.Blocking
import zio.duration._
import zio.clock.Clock
import zio.console.{ Console, putStrLn }
import zio.nio.channels.{ AsynchronousServerSocketChannel, AsynchronousSocketChannel }
import zio.nio.core.SocketAddress
import zio.nio.core.file.Path
import zio.nio.file.Files
import zio.stream.{ ZSink, ZStream }
import zio.{ App, Has, ZEnv, ZIO, ZLayer }

/**
 * Dit werkt:
object LiveService extends ZMailBoxService[ZEnv, Any] {
  //: ZIO[Console with MessageStore, Status, MailBox]
  override def getMailBox(request: GetMailBoxRequest) =
    for {
      _ <- putStrLn(s"GetMailBox for ${request.username}")
      result <- ZIO.succeed(MailBox("test", entries = Seq(), total = 42))
    } yield result

  override def getMessage(request: GetMessageRequest) =
    ZIO.succeed(MailBoxEntry(from = "sender@email.com", to = "nowhere", when = "now", size = 123))
}

object MailServer extends ServerMain {
  def services: ServiceList[zio.ZEnv] = ServiceList.add(LiveService)
}
*/

class LiveService extends ZMailBoxService[ZEnv, Any] {
  //: ZIO[Console with MessageStore, Status, MailBox]
  override def getMailBox(request: GetMailBoxRequest) =
    for {
      _ <- putStrLn(s"GetMailBox for ${request.username}")
      result <- ZIO.succeed(MailBox("test", entries = Seq(), total = 42))
    } yield result

  override def getMessage(request: GetMessageRequest) =
    ZIO.succeed(MailBoxEntry(from = "sender@email.com", to = "nowhere", when = "now", size = 123))
}

object MailServer extends App {
//  def services: ServiceList[zio.ZEnv] = ServiceList.add(LiveService)

  val grpcServer = {
    val serverBuilder = ServerBuilder.forPort(9000).addService(ProtoReflectionService.newInstance())
    ServerLayer.fromService(serverBuilder, new LiveService())
  }

  override def run(args: List[String]) =
    grpcServer.build.useForever.exitCode
}

/**
object MailServer extends App {

  val grpcPort = 9000

  val smtpServer = AsynchronousServerSocketChannel().mapM { socket =>
    for {
      _ <- SocketAddress.inetSocketAddress("0.0.0.0", 8125) >>= socket.bind
      _ <- socket.accept.preallocate
        .flatMap(
          _.ensuring(putStrLn("Connection closed"))
            .use(channel => doWork(channel).catchAll(ex => putStrLn(ex.getMessage + ex.toString)))
            .fork
        )
        .forever
        .fork
      _ <- putStrLn("Mail Server has started")
    } yield ()
  }

  def doWork(channel: AsynchronousSocketChannel): ZIO[Console with Clock with Blocking with MessageStoreT, Throwable, Unit] =
    for {
      rawMessage <- SmtpSession(channel).run
      _ <- putStrLn(s"Finished with a message from ${rawMessage.mailFrom} for ${rawMessage.recipients}.")
      bWritten <- rawMessage.data match {
        case Some(data) =>
          // all to's for fizzle.email
          val recpts = rawMessage.recipients.filter(recp => recp.mailbox.endsWith("@fizzle.email"))
          ZIO.foreachPar(recpts) { recp =>
            writeMsgToFile(data, recp.localName)
          }
        case None => ZIO.fail(new Exception("No data"))
      }
      _ <- ZIO.accessM[MessageStoreT](_.get.newMessage(Message(null, null, null, null, null)))
      _ <- putStrLn(s"Wrote ${bWritten.length} files from ${rawMessage.mailFrom.map(_.path).getOrElse("-")}")
    } yield ()

  def writeMsgToFile(data: String, localName: String) = {
    import java.time._
    val path = s"/tmp/zmail/$localName"
    val now = LocalDateTime.now(ZoneId.of("UTC"))
    val filename = s"$path/$now"
    val from =
      ZStream.fromInputStream(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)))
    val to = ZSink.fromFile(Paths.get(filename))
    for {
      _ <- Files.createDirectories(Path.apply(path))
      _ <- from.run(to)
    } yield ()
  }


}
*/
  /**
  class MailBoxStoreService(store: MessageStore.Service) extends MailBoxService {
    //: ZIO[Console with MessageStore, Status, MailBox]
    override def getMailBox(request: GetMailBoxRequest) =
      for {
        list <- store.getMessages(EmailAddress(request.username)).mapError(x => Status.NOT_FOUND)
      } yield MailBox(request.username, list.msgs.map(msg => MailBoxEntry(from = msg.from.mail, to = msg.to.mail, when = msg.received.toString, 0)))
//      ZIO.succeed(MailBox(request.username, entries = Seq(), total = 42))

    override def getMessage(request: GetMessageRequest) =
      ZIO.succeed(MailBoxEntry(from = "sender@email.com", to = "nowhere", when = "now", size = 123))
  }
  */
/*
  object MailBoxStoreService {
    type MailBoxStoreService = Has[MailBoxService]

    class LiveService(console: Console.Service) extends ZMailBoxService[ZEnv, Any] {
      //: ZIO[Console with MessageStore, Status, MailBox]
      override def getMailBox(request: GetMailBoxRequest) =
        for {
          _ <- console.putStrLn(s"GetMailBox for ${request.username}")
          result <- ZIO.succeed(MailBox("test", entries = Seq(), total = 42))
        } yield result

      override def getMessage(request: GetMessageRequest) = {
        for {
          _ <- console.putStrLn(s"GetMessage for ${request.username} and ${request.id}")
          result <- ZIO.succeed(MailBoxEntry(from = "sender@email.com", to = "nowhere", when = "now", size = 123))
        } yield result
      }
    }

    val live: ZLayer[Console, Nothing, MailBoxStoreService] =
      ZLayer.fromService(new LiveService(_))

  }

  def serverWait: ZIO[Console with Clock, Throwable, Unit] =
    for {
      _ <- putStrLn("Server is running. Press Ctrl-C to stop.")
      _ <- (putStrLn(".") *> ZIO.sleep(1.second)).forever
    } yield ()

  val server = {

  }

  val grpcServer = Console.live >>> MailBoxStoreService.live >>> ServerLayer.access(
    ServerBuilder.forPort(9000).addService(ProtoReflectionService.newInstance())
  )
*/
//      val serverBuilder = ServerBuilder.forPort(grpcPort).addService(ProtoReflectionService.newInstance())
//      ServerLayer.fromService(serverBuilder, new MailBoxStoreService(console))
//  }
//  val grpcServer = ZLayer.fromServiceM { console: Console.Service =>
//      val serverBuilder = ServerBuilder.forPort(grpcPort).addService(ProtoReflectionService.newInstance())
//      ServerLayer.fromService(serverBuilder, new MailBoxStoreService(console))
//  }

//  val messageStore = MessageStore.live
//  val appEnv = MessageStore.live ++ Clock.live ++ Blocking.live ++ Console.live
//  val appEnv = Clock.live ++ Blocking.live ++ Console.live

//  override def run(args: List[String]) =
//    serverWait.provideLayer(grpcServer ++ Console.live ++ Clock.live).exitCode
//    grpcServer.build.useForever.provideLayer(appEnv).exitCode
//    (smtpServer.zip(grpcServer.build)).useForever.provideLayer(appEnv).exitCode
//    (smtpServer).useForever.provideLayer(appEnv).exitCode

//}

/**
val grpcServer = {
    val serverBuilder = ServerBuilder.forPort(grpcPort).addService(ProtoReflectionService.newInstance())
    ServerLayer.fromService(serverBuilder, new AbcService())
  }
 */
