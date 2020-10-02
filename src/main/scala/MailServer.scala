import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.Properties

import javax.mail.Session
import javax.mail.internet.MimeMessage
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
      _ <- SocketAddress.inetSocketAddress("127.0.0.1", 8123) >>= socket.bind
      _ <- socket.accept.preallocate
            .flatMap(_.use(channel => doWork(channel).catchAll(ex => putStrLn(ex.getMessage + ex.toString))).fork)
            .forever
            .fork
      _ <- putStrLn("Mail Server has started")
    } yield ()
  }.useForever

  val greeting    = "220 smtp.voorbeeld.nl Example SMTP Service\n"
  val hello       = "250 Hello\n"
  val pleaseStart = "354 Please start mail input, and finish with a new line and a '.' followed by a newline.\n"
  val queued      = "250 Mail queued for delivery.\n"
  val goodbye     = "221 Goodbye.\n"

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

  def doWork(channel: AsynchronousSocketChannel): ZIO[Console with Clock with Blocking, Throwable, Unit] = {
    val process =
      for {
        _ <- writeToChannel(greeting, channel)
        _ <- putStrLn(s"Wrote greeting to socket")

        data <- readFromChannel(channel)
        _    <- putStrLn(s"Result 1: $data")

        _ <- writeToChannel(hello, channel)
        _ <- putStrLn(s"Wrote hello to socket")

        data <- readFromChannel(channel)
        _    <- putStrLn(s"Result 2: $data")

        _ <- writeToChannel(hello, channel)
        _ <- putStrLn(s"Wrote hello to socket")

        data <- readFromChannel(channel)
        _    <- putStrLn(s"Result 3: $data")

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
