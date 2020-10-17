package email.fizzle.zmail

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.Properties

import email.fizzle.zmail.MailServer.ReplyCode.{ OK, READY, REJECTED }
import javax.mail.internet.MimeMessage
import javax.mail.{ Address, Session }
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.{ putStrLn, Console }
import zio.nio.channels.{ AsynchronousServerSocketChannel, AsynchronousSocketChannel }
import zio.nio.core.SocketAddress
import zio.stream.{ Sink, ZStream, ZTransducer }
import zio.{ App, Chunk, ZIO }

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

  sealed abstract class ReplyCode(val code: Int)
  object ReplyCode {
    case object READY    extends ReplyCode(220)
    case object CLOSING  extends ReplyCode(221)
    case object OK       extends ReplyCode(250)
    case object START    extends ReplyCode(354)
    case object REJECTED extends ReplyCode(550)
  }

  private def respond(code: ReplyCode, txt: String, channel: AsynchronousSocketChannel) = {
    val totalMsg = s"${code.code} $txt\r\n"
    logOutbound(totalMsg) *> writeToChannel(totalMsg, channel)
  }

  private def writeToChannel(txt: String, channel: AsynchronousSocketChannel) =
    ZStream.fromInputStream(new ByteArrayInputStream(txt.getBytes)).foreach { b =>
      for {
        s <- channel.write(Chunk(b))
      } yield s
    }

  def readFromChannel(channel: AsynchronousSocketChannel) =
    ZStream
      .fromEffectOption(
        channel.read(256).orElse(ZIO.fail(None))
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
        all     <- if (proceed) recurse(channel, result :+ chunk)
                   else ZIO.succeed(result :+ chunk)
      } yield all

    recurse(channel, Seq()).map(_.mkString)
  }

  private def printRecipients(recps: Array[Address]) =
    recps.toList.map(address => s"${address.getType} $address").mkString("\n")

  private def logOutbound(txt: String) = putStrLn(s"<<<$txt")
  private def logInbound(txt: String)  = putStrLn(s">>>$txt")

  def stripBrackets(s: String) = {
    val trimmed = s.trim()
    val start   =
      if (trimmed.startsWith("<")) trimmed.substring(1)
      else trimmed
    if (start.endsWith(">")) start.substring(0, start.length - 1)
    else start
  }

  val greeting        = "220 \r\n"
  val hello           = "250 Hello\r\n"
  val pleaseStart     = "354 Please start mail input, and finish with a new line and a '.' followed by a newline.\r\n"
  val queued          = "250 Mail queued for delivery.\r\n"
  val goodbye         = "221 Goodbye.\r\n"
  val failUnknownHost = "555  MAIL FROM/RCPT TO parameters not recognized or not implemented\r\n"

  def doWork(channel: AsynchronousSocketChannel): ZIO[Console with Clock with Blocking, Throwable, Unit] = {
    val process =
      for {
        _          <- putStrLn("New doWork")
        rawMessage <- SmtpSession(channel).run
        _          <- putStrLn(s"Finished with \n$rawMessage")
      } yield ()

    // read the entire message, which might fail because of wrong recipients or other problems
// store the session transcript (which should never fail)
// store the message somewhere
// in case of success, parse with javamail into - ja wat eigenlijk?

    process.whenM(channel.isOpen).forever
  }

  override def run(args: List[String]) =
    server.exitCode

}
