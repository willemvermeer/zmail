package email.fizzle.zmail

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Paths

import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.{ Console, putStrLn }
import zio.nio.channels.{ AsynchronousServerSocketChannel, AsynchronousSocketChannel }
import zio.nio.core.SocketAddress
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

  def doWork(channel: AsynchronousSocketChannel): ZIO[Console with Clock with Blocking, Throwable, Unit] = {
    val dest    = "/tmp/willem"
    val process =
      for {
        rawMessage <- SmtpSession(channel).run
        _          <- putStrLn(s"Finished with a message from ${rawMessage.mailFrom} for ${rawMessage.recipients}.")
        bWritten   <- rawMessage.data match {
                        case Some(data) =>
                          val from =
                            ZStream.fromInputStream(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)))
                          val to   = ZSink.fromFile(Paths.get(dest))
                          from.run(to)
                        case None       => ZIO.fail(new Exception("No data"))
                      }
        _          <- putStrLn(s"Wrote $bWritten bytes to $dest")
      } yield ()

    // read the entire message, which might fail because of wrong recipients or other problems
// store the session transcript (which should never fail)
// store the message somewhere
// in case of success, parse with javamail into - ja wat eigenlijk?

//    process.whenM(channel.isOpen).forever
    process
  }

  override def run(args: List[String]) =
    server.exitCode

}
