package email.fizzle.zmail

import java.time.LocalDateTime

import zio.{ Has, IO, Layer, Ref, UIO, ZIO }

case class MessageId(id: String)
case class EmailAddress(mail: String)
case class Message(from: EmailAddress, to: EmailAddress, subject: String, received: LocalDateTime, id: MessageId)
case class MessageList(msgs: Seq[Message]) {
  def add(msg: Message): MessageList = MessageList(msgs :+ msg)
}

object MessageStore {

  type MessageStore = Has[MessageStore.Service]
  type StoreElt = Map[EmailAddress, MessageList]

  trait Service {
    def newMessage(msg: Message): UIO[Unit]
    def getMessages(name: EmailAddress): IO[MessageError, MessageList]
  }

  sealed trait MessageError {
    def msg: String
  }
  final case class EmptyMailboxError(msg: String) extends MessageError

  case class MessageStoreImpl(store: Ref[StoreElt]) extends Service {
    override def newMessage(msg: Message): UIO[Unit] =
      for {
        ref <- store.get
        _   <- ref.get(msg.to) match {
                 case None              => store.update(_ + (msg.from -> MessageList(Seq(msg))))
                 case Some(messageList) => store.update(_ + (msg.from -> messageList.add(msg)))
               }
      } yield ()

    override def getMessages(name: EmailAddress): IO[MessageError, MessageList] =
      for {
        ref    <- store.get
        result <- ref.get(name) match {
                    case None      => ZIO.fail(EmptyMailboxError(s"No messages for ${name.mail}"))
                    case Some(seq) => ZIO.succeed(seq)
                  }
      } yield result
  }

  val live: Layer[Nothing, MessageStore] = Ref.make(Map[EmailAddress, MessageList]()).map(MessageStoreImpl).toLayer

}
