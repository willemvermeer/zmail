package zmail

import java.time.LocalDateTime
import java.util.UUID

import zio._
import zio.stream.{ Stream, ZStream }

case class MessageId(id: String = UUID.randomUUID().toString)
case class EmailAddress(mail: String)
case class Message(
  from: EmailAddress,
  to: EmailAddress,
  recipients: Seq[EmailAddress],
  subject: String,
  received: LocalDateTime,
  id: MessageId,
  data: String
)
case class MessageList(msgs: Seq[Message] = Seq()) {
  def add(msg: Message): MessageList = MessageList(msgs :+ msg)
}

object MessageStore {

  type MessageStore = Has[MessageStore.Service]
  type StoreElt     = Map[EmailAddress, MessageList]

  trait Service {
    def storeMessage(msg: Message): UIO[Unit]
    def getMessages(name: EmailAddress): UIO[MessageList]
    def getMessage(name: EmailAddress, id: MessageId): IO[MessageError, Message]
    def messageStream(name: EmailAddress): ZStream[Any, Nothing, Message]
  }

  sealed trait MessageError {
    def msg: String
  }
  final case class EmptyMailboxError(msg: String)   extends MessageError
  final case class UnknownMessageError(msg: String) extends MessageError

  case class MessageStoreImpl(store: Ref[StoreElt], msgQueue: Queue[Message]) extends Service {
    override def storeMessage(msg: Message): UIO[Unit] =
      for {
        ref <- store.get
        _   <- ZIO.effectTotal(println(s"Storing message $msg"))
        _   <- ref.get(msg.to) match {
                 case None              => store.update(_ + (msg.to -> MessageList(Seq(msg))))
                 case Some(messageList) => store.update(_ + (msg.to -> messageList.add(msg)))
               }
        _   <- msgQueue.offer(msg) // add to queue so the streaming grpc call will receive an update
      } yield ()

    override def getMessages(name: EmailAddress): UIO[MessageList] =
      for {
        ref    <- store.get
        result <- ref.get(name) match {
                    case None      => ZIO.succeed(MessageList())
                    case Some(seq) => ZIO.succeed(seq)
                  }
      } yield result

    override def getMessage(name: EmailAddress, id: MessageId): IO[MessageError, Message] =
      for {
        ref    <- store.get
        result <- ref.get(name) match {
                    case None      => ZIO.fail(EmptyMailboxError(s"No messages for ${name.mail}"))
                    case Some(seq) =>
                      seq.msgs.find(m => m.id == id) match {
                        case None      => ZIO.fail(UnknownMessageError(s"Could not find message with ID ${id.id}"))
                        case Some(msg) => ZIO.succeed(msg)
                      }
                  }
      } yield result

    val allMessages = Stream.fromQueue(msgQueue)

    override def messageStream(name: EmailAddress) =
      allMessages.filter(msg => msg.to == name)
  }

  val live: Layer[Nothing, MessageStore] =
    (for {
      ref   <- Ref.make(Map[EmailAddress, MessageList]())
      queue <- Queue.unbounded[Message]
    } yield MessageStoreImpl(ref, queue)).toLayer

}
