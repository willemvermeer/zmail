package zmail.smtp

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.time.{ LocalDateTime, ZoneId }
import java.util.Properties

import javax.mail.Session
import javax.mail.internet.MimeMessage
import zio.blocking.Blocking
import zio.clock.Clock
import zio.config.getConfig
import zio.console.{ putStrLn, Console }
import zio.nio.channels.{ AsynchronousServerSocketChannel, AsynchronousSocketChannel }
import zio.nio.core.SocketAddress
import zio.nio.core.charset.Charset
import zio.nio.core.file.Path
import zio.nio.file.Files
import zio.stream.{ ZSink, ZStream }
import zio.{ Chunk, ZIO }
import zmail.MessageStore.MessageStore
import zmail.{ EmailAddress, Message, MessageId, ZmailConfig }

object SmtpServer {

  val smtpServer = AsynchronousServerSocketChannel().mapM { socket =>
    for {
      config  <- getConfig[ZmailConfig]
      _       <- putStrLn(s"Mounting SMTP server on port ${config.smtp.port}")
      address <- SocketAddress.inetSocketAddress("0.0.0.0", config.smtp.port)
      _       <- socket.bind(address)
      _       <- socket.accept.preallocate
                   .flatMap(
                     _.ensuring(putStrLn("Connection closed"))
                       .use(channel =>
                         receiveMail(channel, config.domain, config.tmpDir)
                           .catchAll(ex => putStrLn(ex.getMessage + ex.toString))
                       )
                       .fork
                   )
                   .forever
                   .fork
    } yield ()
  }

  private def receiveMail(
    channel: AsynchronousSocketChannel,
    domain: String,
    tmpDir: String
  ): ZIO[Console with Clock with Blocking with MessageStore, Throwable, Unit] =
    for {
      rawMessage <- SmtpSession(channel).run
      _          <- putStrLn(s"Finished with a message from ${rawMessage.mailFrom} for ${rawMessage.recipients}.")
      bWritten   <- rawMessage.data match {
                      case Some(data) =>
                        val recpts = rawMessage.recipients.filter(_.mailbox.endsWith(s"@$domain"))
                        ZIO.foreachPar(recpts) { recp =>
                          storeNewMessage(rawMessage, data, recp, tmpDir).catchAll(ex => putStrLn(s"Exception $ex"))
                        }
                      case None       => ZIO.fail(new Exception("No data"))
                    }
      _          <- putStrLn(s"Wrote ${bWritten.length} files from ${rawMessage.mailFrom.map(_.path).getOrElse("-")}")
    } yield ()

  private def storeNewMessage(rawMessage: RawMessage, data: String, recp: Recipient, tmpDir: String) =
    for {
      _       <- putStrLn(s"Adding message for ${recp.localName}")
      _       <- writeMsgToFile(data, recp.localName, tmpDir)
      message <- ZIO.fromOption(fromRaw(rawMessage, recp.mailbox))
      _       <- putStrLn(s"Storing message $message")
      _       <- ZIO.accessM[MessageStore](_.get.storeMessage(message))
    } yield ()

  private def writeMsgToFile(data: String, localName: String, tmpDir: String) = {
    val path      = s"$tmpDir/$localName"
    val now       = dateTimeNow
    val filename  = s"$path/$now"
    val dataChunk = Chunk.fromArray(data.toCharArray)
    val from      = ZStream.fromChunk(dataChunk)
    val to        = ZSink.fromFile(Paths.get(filename))
    for {
      _ <- Files.createDirectories(Path.apply(path))
      _ <- from.transduce(Charset.Standard.utf8.newEncoder.transducer()).run(to)
    } yield ()
  }

  private def fromRaw(rawMessage: RawMessage, to: String): Option[Message] =
    (rawMessage.mailFrom, rawMessage.data) match {
      // TODO: catch exceptions!!
      case (Some(from), Some(data)) =>
        // read the message body to extract the subject
        val session     = Session.getDefaultInstance(new Properties())
        val mimeMessage = new MimeMessage(session, new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)))
        Some(
          Message(
            from = EmailAddress(from.path.path),
            to = EmailAddress(to),
            recipients = rawMessage.recipients.map(r => EmailAddress(r.mailbox)),
            subject = mimeMessage.getSubject,
            received = dateTimeNow,
            id = MessageId(),
            data = data
          )
        )
      case _                        => None
    }

  private def dateTimeNow = LocalDateTime.now(ZoneId.of("UTC"))
}
