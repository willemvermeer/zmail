package email.fizzle.zmail

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Paths

import email.fizzle.service.ZioService._
import email.fizzle.service.{ GetMailBoxRequest, GetMessageRequest, MailBox, MailBoxEntry }
import email.fizzle.zmail.MessageStore.MessageStore
import io.grpc.protobuf.services.ProtoReflectionService
import io.grpc.{ ServerBuilder, Status }
import scalapb.zio_grpc.ServerLayer
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.{ putStrLn, Console }
import zio.nio.channels.{ AsynchronousServerSocketChannel, AsynchronousSocketChannel }
import zio.nio.core.SocketAddress
import zio.nio.core.file.Path
import zio.nio.file.Files
import zio.stream.{ ZSink, ZStream }
import zio.{ App, ZIO, ZLayer }

class LiveService(messageStore: MessageStore.Service, console: Console.Service) extends MailBoxService {
  override def getMailBox(request: GetMailBoxRequest) =
    for {
      _      <- console.putStrLn(s"GetMailBox for ${request.username}")
      msgs   <- messageStore
                  .getMessages(EmailAddress(request.username))
                  .mapError(x => Status.INTERNAL) // TODO: better error mgmt
      result <- ZIO.succeed(MailBox("test", entries = Seq(), total = 42))
      _      <- console.putStrLn(s"Found $msgs in store")
    } yield result

  override def getMessage(request: GetMessageRequest) =
    ZIO.succeed(MailBoxEntry(from = "sender@email.com", to = "nowhere", when = "now", size = 123))
}

object MailServer extends App {

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

  def doWork(
    channel: AsynchronousSocketChannel
  ): ZIO[Console with Clock with Blocking with MessageStore, Throwable, Unit] =
    for {
      rawMessage <- SmtpSession(channel).run
      _          <- putStrLn(s"Finished with a message from ${rawMessage.mailFrom} for ${rawMessage.recipients}.")
      bWritten   <- rawMessage.data match {
                      case Some(data) =>
                        // all to's for fizzle.email
                        val recpts = rawMessage.recipients.filter(recp => recp.mailbox.endsWith("@fizzle.email"))
                        ZIO.foreachPar(recpts) { recp =>
                          putStrLn(s"Adding message for ${recp.localName}") *>
                            writeMsgToFile(data, recp.localName) *>
                            ZIO.accessM[MessageStore](
                              _.get.newMessage(Message(EmailAddress(recp.localName), null, null, null, null))
                            )
                        }
                      case None       => ZIO.fail(new Exception("No data"))
                    }
      _          <- putStrLn(s"Wrote ${bWritten.length} files from ${rawMessage.mailFrom.map(_.path).getOrElse("-")}")
    } yield ()

  def writeMsgToFile(data: String, localName: String) = {
    import java.time._
    val path     = s"/tmp/zmail/$localName"
    val now      = LocalDateTime.now(ZoneId.of("UTC"))
    val filename = s"$path/$now"
    val from     =
      ZStream.fromInputStream(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)))
    val to       = ZSink.fromFile(Paths.get(filename))
    for {
      _ <- Files.createDirectories(Path.apply(path))
      _ <- from.run(to)
    } yield ()
  }

  val grpcPort = 9000

  def welcome: ZIO[Console with MessageStore, Throwable, Unit] =
    for {
      _    <- putStrLn("Grpc server has started.")
      msgs <- ZIO
                .accessM[MessageStore](_.get.getMessages(EmailAddress("willem.vermeer")))
                .catchAll(x => putStrLn((s"Error ${x.msg} ignored")) *> ZIO.succeed(0))
      _    <- putStrLn(s"Store has $msgs")
    } yield ()

  val messageStore = MessageStore.live
  val liveService  =
    ZLayer.fromServices[MessageStore.Service, Console.Service, MailBoxService] {
      (store: MessageStore.Service, console: Console.Service) =>
        new LiveService(store, console)
    }
  val grpcServer   = ServerLayer.access[MailBoxService] {
    val s: ServerBuilder[_] = ServerBuilder.forPort(grpcPort).addService(ProtoReflectionService.newInstance())
    s
  }

  val appEnv =
    (Console.live ++ messageStore) >>> liveService ++ messageStore ++ Clock.live ++ Blocking.live ++ Console.live

  override def run(args: List[String]) =
    (for {
      _ <- smtpServer
      _ <- (welcome.toLayer ++ grpcServer).build
    } yield ()).useForever.provideLayer(appEnv).exitCode
}
