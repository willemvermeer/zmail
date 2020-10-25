package email.fizzle.zmail

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Paths

import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.{ putStrLn, Console }
import zio.nio.channels.{ AsynchronousServerSocketChannel, AsynchronousSocketChannel }
import zio.nio.core.SocketAddress
import zio.nio.core.file.Path
import zio.nio.file.Files
import zio.stream.{ ZSink, ZStream }
import zio.{ App, ZIO }

object MailServer extends App {

  val server = AsynchronousServerSocketChannel().mapM { socket =>
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
  }.useForever

  def doWork(channel: AsynchronousSocketChannel): ZIO[Console with Clock with Blocking, Throwable, Unit] =
    for {
      rawMessage <- SmtpSession(channel).run
      _          <- putStrLn(s"Finished with a message from ${rawMessage.mailFrom} for ${rawMessage.recipients}.")
      bWritten   <- rawMessage.data match {
                      case Some(data) =>
                        // all to's for fizzle.email
                        val recpts = rawMessage.recipients.filter(recp => recp.mailbox.endsWith("@fizzle.email"))
                        ZIO.foreachPar(recpts) { recp =>
                          writeMsgToFile(data, recp.localName)
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

  override def run(args: List[String]) =
    server.exitCode

}
