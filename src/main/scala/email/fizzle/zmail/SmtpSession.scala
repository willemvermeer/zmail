package email.fizzle.zmail

import java.io.ByteArrayInputStream

import email.fizzle.zmail.ReplyCode._
import email.fizzle.zmail.Smtp._
import zio.blocking.Blocking
import zio.console.{ putStrLn, Console }
import zio.nio.channels.AsynchronousSocketChannel
import zio.stream.{ Sink, ZStream, ZTransducer }
import zio.{ Chunk, IO, ZIO }

import scala.collection.mutable

case class SmtpSession(channel: AsynchronousSocketChannel, log: Log = new Log()) {

  def run = (for {
    msg <- handshake
    msg <- main(msg)
  } yield msg).tapError(x => putStrLn(s"\n###############ERROR $x\n${log.logs.mkString("\n")}"))

  def handshake =
    for {
      remoteAddress <- channel.remoteAddress.orElseSucceed(None)
      _             <- logInbound(s"Begin interaction with $remoteAddress")
      _             <- respond(READY, "fizzle.email Fizzle SMTP Service")
    } yield RawMessage()

  def main(msg: RawMessage) = {

    def nextCommand =
      for {
        line    <- readLineFromChannel
        _       <- logInbound(line)
        command <- SmtpParser.parse(line)
      } yield command

    def handleCommand(cmd: Command, msg: RawMessage) = cmd match {
      case Helo(domain)           =>
        for {
          _ <- respond(ReplyCode.OK, "Hello")
        } yield msg.addDomain(domain)
      case Ehlo(domain)           =>
        for {
          // TODO: tell client what msgs we support
          _ <- respond(ReplyCode.OK, "Hello")
        } yield msg.addDomain(domain)
      case mailFrom @ MailFrom(_) =>
        for {
          _ <- respond(ReplyCode.OK, "OK")
        } yield msg.addMailFrom(mailFrom)
      case rcptTo @ RcptTo(_)     =>
        for {
          _ <- respond(ReplyCode.OK, "OK")
        } yield msg.addRecipient(Recipient(rcptTo.path.path))
      case Data                   =>
        for {
          _    <- respond(ReplyCode.START, "Ready to receive data")
          data <- readAllFromChannel(channel)
          _    <- respond(ReplyCode.OK, "Mail queued.")
        } yield msg.addData(data)
    }

    def recurse(cmd: Command, msg: RawMessage): ZIO[Blocking with Console, Exception, RawMessage] =
      cmd match {
        case Quit =>
          for {
            _ <- respond(ReplyCode.CLOSING, "OK")
          } yield msg
        case _    =>
          for {
            msg <- handleCommand(cmd, msg)
            cmd <- nextCommand
            msg <- recurse(cmd, msg)
          } yield msg
      }

    for {
      cmd <- nextCommand
      msg <- recurse(cmd, msg)
    } yield msg

  }

  private def respond(replyCode: ReplyCode, txt: String) = {
    val totalMsg = s"${replyCode.code} $txt\r\n"
    logOutbound(totalMsg) *> writeToChannel(totalMsg, channel)
  }

  private def writeToChannel(txt: String, channel: AsynchronousSocketChannel) =
    ZStream.fromInputStream(new ByteArrayInputStream(txt.getBytes)).foreach { b =>
      for {
        s <- channel.writeChunk(Chunk(b))
      } yield s
    }

  private def logOutbound(txt: String) = ZIO.succeed(log.append(s"<<<$txt"))
  private def logInbound(txt: String)  = ZIO.succeed(log.append(s">>>$txt"))

  private val MAX_LINE_LENGTH = 1024 // according to rfc 5322 the maximum line length is 998 characters
  // private val MAX_MSG_SIZE = 1024 * 1024 // put a 1Mb limit on the message size to avoid disk space exhaustion

  private def readLineFromChannel =
    ZStream
      .fromEffectOption(
        channel.readChunk(MAX_LINE_LENGTH).tap(c => putStrLn(s"Read chunk of length ${c.size}")).orElse(ZIO.fail(None))
      )
      .flattenChunks
      .transduce(ZTransducer.utf8Decode)
      .run(Sink.foldLeft("")(_ + _))

  private def readAllFromChannel(channel: AsynchronousSocketChannel) = {
    def recurse(channel: AsynchronousSocketChannel, result: Seq[String]): ZIO[Console, Nothing, Seq[String]] =
      for {
        chunk   <- readLineFromChannel
        proceed <- ZIO.succeed(chunk.length == MAX_LINE_LENGTH)
        all     <- if (proceed) recurse(channel, result :+ chunk)
                   else ZIO.succeed(result :+ chunk)
      } yield all

    recurse(channel, Seq()).map(_.mkString)
  }

}

class Log {
  val logs = mutable.ListBuffer[String]()
  def append(txt: String) = {
    logs += txt
    println(txt)
    logs
  }
}
/*

msg <- ZIO.effect {
val session = Session.getDefaultInstance(new Properties())
new MimeMessage(session, new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)))
}
 */
