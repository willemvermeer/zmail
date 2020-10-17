package email.fizzle.zmail

import java.io.ByteArrayInputStream

import email.fizzle.zmail.MailServer.ReplyCode.READY
import email.fizzle.zmail.MailServer.{ readFromChannel, ReplyCode }
import email.fizzle.zmail.Smtp._
import zio.blocking.Blocking
import zio.nio.channels.AsynchronousSocketChannel
import zio.stream.{ Sink, ZStream, ZTransducer }
import zio.{ Chunk, IO, ZIO }

import scala.collection.mutable

case class SmtpSession(channel: AsynchronousSocketChannel, log: Log = new Log()) {

  def run = for {
    msg <- handshake
    msg <- main(msg)
  } yield msg

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

    def recurse(cmd: Command, msg: RawMessage): ZIO[Blocking, Exception, RawMessage] =
      cmd match {
        case Quit                => ZIO.succeed(msg)
        case Helo(domain)        =>
          for {
            msg <- helo(domain, msg)
            cmd <- nextCommand
            msg <- recurse(cmd, msg)
          } yield msg
        case Ehlo(domain)        =>
          for {
            msg <- ehlo(domain, msg)
            cmd <- nextCommand
            msg <- recurse(cmd, msg)
          } yield msg
        case mf @ MailFrom(path) =>
          for {
            msg <- mailFrom(mf, msg)
            cmd <- nextCommand
            msg <- recurse(cmd, msg)
          } yield msg
        case mf @ RcptTo(path)   =>
          for {
            msg <- rcptTo(mf, msg)
            cmd <- nextCommand
            msg <- recurse(cmd, msg)
          } yield msg
        case Data                =>
          for {
            msg <- data(msg)
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
    } yield msg.setData(data)

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
  private def logInbound(txt: String)  = ZIO.succeed(log.append(s">>>$txt###"))

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
        chunk   <- readFromChannel(channel)
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
remoteAddress <- channel.remoteAddress.orElseSucceed(None)
_             <- putStrLn(s"Begin interaction with $remoteAddress")

_ <- respond(READY, "fizzle.email Fizzle SMTP Service", channel)

clientHost <- readFromChannel(channel)
_          <- logInbound(clientHost)
_          <- respond(OK, "Hello", channel)

mailFrom <- readFromChannel(channel)
_        <- logInbound(mailFrom)
_        <- respond(OK, s"${

import zio.blocking.Blocking

stripBrackets(mailFrom)}", channel)

rcptTo <- readFromChannel(channel)
_      <- logInbound(rcptTo)
_ <- if (!rcptTo.contains("@fizzle.email")) {
respond(REJECTED, "No such user here", channel) *>
channel.shutdownOutput *>
ZIO.fail(IllegalFromException(rcptTo))
} else ZIO.succeed(mailFrom)
_ <- respond(OK, s"${stripBrackets(mailFrom)}", channel)

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
 */
