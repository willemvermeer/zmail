import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.Properties

import javax.mail.internet.MimeMessage
import javax.mail.{ Address, Session }
import zio._
import zio.blocking.Blocking
import zio.clock._
import zio.console._
import zio.nio.channels._
import zio.nio.core._
import zio.stream.{ Sink, ZStream, ZTransducer }

object MailServer extends App {

  val server = AsynchronousServerSocketChannel().mapM { socket =>
    for {
      _ <- SocketAddress.inetSocketAddress("0.0.0.0", 25) >>= socket.bind
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

  val greeting        = "220 fizzle.email Fizzle SMTP Service\r\n"
  val hello           = "250 Hello\r\n"
  val pleaseStart     = "354 Please start mail input, and finish with a new line and a '.' followed by a newline.\r\n"
  val queued          = "250 Mail queued for delivery.\r\n"
  val goodbye         = "221 Goodbye.\r\n"
  val failUnknownHost = "555  MAIL FROM/RCPT TO parameters not recognized or not implemented\r\n"

  def writeToChannel(txt: String, channel: AsynchronousSocketChannel) =
    ZStream.fromInputStream(new ByteArrayInputStream(txt.getBytes)).foreach { b =>
      for {
        s <- channel.write(Chunk(b))
      } yield s
    }

  def readFromChannel(channel: AsynchronousSocketChannel) =
    ZStream
      .fromEffectOption(
        channel.read(256).tap(c => putStrLn(s"Read chunk with size ${c.length}")).orElse(ZIO.fail(None))
      )
      .flattenChunks
      .take(256)
      .transduce(ZTransducer.utf8Decode)
      .run(Sink.foldLeft("")(_ + (_: String)))

  case class EndOfStreamException(message: String) extends Exception(message: String)
  case class IllegalFromException(message: String) extends Exception(message: String)

  def readAllFromChannel(channel: AsynchronousSocketChannel): ZIO[Console, Nothing, String] = {
    def recurse(channel: AsynchronousSocketChannel, result: Seq[String]): ZIO[Console, Nothing, Seq[String]] =
      for {
        chunk   <- readFromChannel(channel)
        proceed <- ZIO.succeed(chunk.length == 256)
        all <- if (proceed) recurse(channel, result :+ chunk)
              else ZIO.succeed(result :+ chunk)
      } yield all

    recurse(channel, Seq()).map(_.mkString)
  }

  private def printRecipients(recps: Array[Address]) =
    recps.toList.map(address => s"${address.getType} $address").mkString("\n")

  def doWork(channel: AsynchronousSocketChannel): ZIO[Console with Clock with Blocking, Throwable, Unit] = {
    val process =
      for {
        remoteAddress <- channel.remoteAddress.orElseSucceed(None)
        _ <- putStrLn(s"Begin handling connection from $remoteAddress")

        _ <- writeToChannel(greeting, channel)
        _ <- putStrLn(s"Wrote greeting to socket")

        clientHost <- readFromChannel(channel)
        _          <- putStrLn(s"Result 1: $clientHost")

        _ <- writeToChannel(hello, channel)
        _ <- putStrLn(s"Wrote hello to socket")

        mailFrom <- readFromChannel(channel)
        _        <- putStrLn(s"Result 2: $mailFrom")

        _ <- writeToChannel(hello, channel)
        _ <- putStrLn(s"Wrote hello to socket")

        rcptTo <- readFromChannel(channel)
        _    <- putStrLn(s"Result 3: $rcptTo")
        _ <- if (rcptTo.contains("@gmail.com")) {
          writeToChannel(failUnknownHost, channel) *>
            channel.shutdownOutput *>
            ZIO.fail(IllegalFromException(rcptTo))
        } else ZIO.succeed(mailFrom)

        _ <- writeToChannel(hello, channel)
        _ <- putStrLn(s"Wrote hello to socket")

        data <- readFromChannel(channel)
        _    <- putStrLn(s"Result 4: $data")

        _ <- writeToChannel(pleaseStart, channel)
        _ <- putStrLn(s"Wrote hello to socket")

        data <- readAllFromChannel(channel)
        _    <- putStrLn(s"Result 5: $data")

        msg <- ZIO.effect {
                val session = Session.getDefaultInstance(new Properties())
                new MimeMessage(session, new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)))
              }
        _ <- putStrLn(s"Da MimeMessage: ${msg.getSender}")
        _ <- putStrLn(s"Da MimeMessage: ${printRecipients(msg.getAllRecipients)}")
        _ <- putStrLn(s"Da MimeMessage: ${msg.getMessageID}")
        _ <- putStrLn(s"Da MimeMessage: ${msg.getContentType}")

        _ <- writeToChannel(queued, channel)
        _ <- putStrLn(s"Wrote queued to socket")

        data <- readFromChannel(channel)
        _    <- putStrLn(s"Result 6: $data")

        _ <- writeToChannel(goodbye, channel)
        _ <- putStrLn(s"Wrote goodbye to socket")

        _ <- channel.shutdownInput
        _ <- putStrLn("Shutdown input")
        _ <- channel.shutdownOutput
        _ <- putStrLn("Shutdown output")
      } yield ()

    process.whenM(channel.isOpen).forever
  }

  override def run(args: List[String]) =
    server.exitCode

}
