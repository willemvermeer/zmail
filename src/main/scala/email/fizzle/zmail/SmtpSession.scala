package email.fizzle.zmail

import java.io.ByteArrayInputStream

import email.fizzle.zmail.ReplyCode._
import email.fizzle.zmail.Smtp._
import zio.blocking.Blocking
import zio.console.putStrLn
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
        line <- readLineFromChannel
        _       <- logInbound(line)
        command <- SmtpParser.parse(line)
      } yield command

    def handle(cmd: Command, msg: RawMessage) = cmd match {
      case Helo(domain)        =>
        for {
          msg <- helo(domain, msg)
        } yield msg
      case Ehlo(domain)        =>
        for {
          msg <- ehlo(domain, msg)
        } yield msg
      case mf @ MailFrom(path) =>
        for {
          msg <- mailFrom(mf, msg)
        } yield msg
      case mf @ RcptTo(path)   =>
        for {
          msg <- rcptTo(mf, msg)
        } yield msg
      case Data                =>
        for {
          msg <- data(msg)
        } yield msg
    }
    def recurse(cmd: Command, msg: RawMessage): ZIO[Blocking, Exception, RawMessage] =
      cmd match {
        case Quit                =>
          for {
            msg <- quit(msg)
          } yield msg
        case _ =>
          for {
            msg <- handle(cmd, msg)
            cmd <- nextCommand
            msg <- recurse(cmd, msg)
          } yield msg
      }

    for {
      cmd <- nextCommand
      msg <- recurse(cmd, msg)
    } yield msg

  }

  val hello = "Hello"

  private def helo(domain: Domain, msg: RawMessage) =
    for {
      _ <- respond(ReplyCode.OK, hello)
    } yield msg.addDomain(domain)

  private def ehlo(domain: Domain, msg: RawMessage) =
    for {
      // TODO: tell client what msgs we support
      _ <- respond(ReplyCode.OK, hello)
    } yield msg.addDomain(domain)

  private def mailFrom(mailFrom: MailFrom, msg: RawMessage) =
    for {
      _ <- respond(ReplyCode.OK, s"OK")
    } yield msg.addMailFrom(mailFrom)

  private def rcptTo(rcptTo: RcptTo, msg: RawMessage) =
    for {
      _ <- respond(ReplyCode.OK, s"OK")
    } yield msg.addRecipient(Recipient(rcptTo.path.path))

  private def data(msg: RawMessage) =
    for {
      _    <- respond(ReplyCode.START, s"Ready to receive data")
      data <- readAllFromChannel(channel)
      _    <- respond(ReplyCode.OK, s"Mail queued.")
    } yield msg.setData(data)

  private def quit(msg: RawMessage) =
    for {
      _ <- respond(ReplyCode.CLOSING, "OK")
    } yield msg

  private def respond(code: ReplyCode, txt: String) = {
    val totalMsg = s"${code.code} $txt\r\n"
    logOutbound(totalMsg) *> writeToChannel(totalMsg, channel)
  }

  private def writeToChannel(txt: String, channel: AsynchronousSocketChannel) =
    ZStream.fromInputStream(new ByteArrayInputStream(txt.getBytes)).foreach { b =>
      for {
        s <- channel.write(Chunk(b))
      } yield s
    }

  private def logOutbound(txt: String) = ZIO.succeed(log.append(s"<<<$txt"))
  private def logInbound(txt: String)  = ZIO.succeed(log.append(s">>>$txt"))

  private def readLineFromChannel =
    ZStream
      .fromEffectOption(
        channel.read(256).orElse(ZIO.fail(None))
      )
      .flattenChunks
      .take(256)
      .transduce(ZTransducer.utf8Decode)
      .run(Sink.foldLeft("")(_ + (_: String)))

  private def readAllFromChannel(channel: AsynchronousSocketChannel) = {
    def recurse(channel: AsynchronousSocketChannel, result: Seq[String]): IO[Nothing, Seq[String]] =
      for {
        chunk   <- readLineFromChannel
        proceed <- ZIO.succeed(chunk.length == 256)
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
