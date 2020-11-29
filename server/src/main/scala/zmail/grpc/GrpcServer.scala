package zmail.grpc

import io.grpc.protobuf.services.ProtoReflectionService
import io.grpc.{ ServerBuilder, Status }
import scalapb.zio_grpc.ServerLayer
import zio.console.Console
import zio.stream.Stream
import zio.{ ZIO, ZLayer }
import zmail.MessageStore.Service
import zmail.service.ZioService.MailBoxService
import zmail.service._
import zmail.{ EmailAddress, MessageId, MessageStore }

class LiveMailBoxService(messageStore: MessageStore.Service, console: Console.Service) extends MailBoxService {

  override def getMailBox(request: GetMailBoxRequest) = {
    val username = request.username
    for {
      msgs   <- messageStore.getMessages(EmailAddress(username))
      result <-
        ZIO.succeed(
          MailBox(
            entries =
              msgs.msgs.map(m => MailBoxEntry(from = m.from.mail, to = m.to.mail, subject = m.subject, id = m.id.id)),
            total = msgs.msgs.size
          )
        )
    } yield result
  }

  override def getMessage(request: GetMessageRequest) =
    for {
      _     <- console.putStrLn(s"Received getMessage request for ${request.username} and id ${request.id}")
      msg   <- messageStore
                 .getMessage(EmailAddress(request.username), MessageId(request.id))
                 .mapError(_ => Status.INTERNAL) // TODO: better error mgmt
      entry <- ZIO.succeed(MailBoxEntry(from = msg.from.mail, body = msg.data))
    } yield entry

  override def getMessageStream(request: GetMailBoxRequest): Stream[Status, MailBoxEntry] =
    messageStore
      .messageStream(EmailAddress(request.username))
      .map(m => MailBoxEntry(from = m.from.mail, to = m.to.mail, subject = m.subject, id = m.id.id))
      .tap(x => console.putStrLn(s"Downing entry $x"))
      .mapError(_ => Status.OK)
}

object GrpcServer {
  val service =
    ZLayer.fromServices[Service, Console.Service, MailBoxService] {
      (store: MessageStore.Service, console: Console.Service) =>
        new LiveMailBoxService(store, console)
    }

  def server(port: Int) =
    ServerLayer
      .access[MailBoxService] {
        val s: ServerBuilder[_] = ServerBuilder.forPort(port).addService(ProtoReflectionService.newInstance())
        s
      }
      .build

}
