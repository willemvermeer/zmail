package zmail.web

import java.io

import org.scalajs.dom
import org.scalajs.dom.MouseEvent
import org.scalajs.dom.raw.{ HTMLTableElement, HTMLTableRowElement }
import scalapb.grpc.Channels
import scalapb.zio_grpc.ZManagedChannel
import zio.console.{ putStrLn, Console }
import zio.stream.Stream
import zio.{ App, Queue, UIO, ZIO }
import zmail.service.ZioService.MailBoxServiceClient
import zmail.service._

object WebappMain extends App {

  sealed trait Event
  final case class RowClickEvent(msgId: String)       extends Event
  final case class MessageReceivedEvent(body: String) extends Event

  sealed trait State
  case object Idle extends State

  val domain                        = "fizzle.email" // TODO: get from config, how??
  def withDomain(localName: String) = s"$localName@$domain"

  val appLogic: ZIO[Console with MailBoxServiceClient, io.Serializable, Unit] = for {
    name     <- ZIO.succeed(new dom.experimental.URL(dom.window.location.href).searchParams.get("name"))
    response <- MailBoxServiceClient
                  .getMailBox(GetMailBoxRequest(username = withDomain(name)))
                  .catchAll(x => putStrLn(s"$x") *> ZIO.succeed(MailBox()))
    q        <- Queue.unbounded[Event]
    _        <- fillTable(response, q)
    _        <- MailBoxServiceClient
                  .getMessageStream(GetMailBoxRequest(username = withDomain(name)))
                  .foreach(addRow(_, q))
                  .fork
    _        <- Stream
                  .fromQueue(q)
                  .foldM(Idle: State) {
                    case (Idle, RowClickEvent(msgId))       =>
                      for {
                        msg <- MailBoxServiceClient.getMessage(GetMessageRequest(withDomain(name), msgId))
                        _   <- q.offer(MessageReceivedEvent(msg.body))
                      } yield Idle
                    case (Idle, MessageReceivedEvent(body)) =>
                      for {
                        escapedBody <- HtmlEscaper.escape(body)
                        _           <- ZIO.effectTotal {
                                         dom.document.getElementById("mailbody").innerHTML = wrapMsgBody(escapedBody)
                                       }
                      } yield Idle
                  }
  } yield ()

  private def wrapMsgBody(body: String) =
    s"""<figure>
       |  <pre style="white-space: pre-line;">
       |    <code>
       |      $body
       |    </code>
       |  </pre>
       |</figure>""".stripMargin

  private def fillTable(mailbox: MailBox, q: Queue[Event]) =
    ZIO.foreach_(mailbox.entries)(addRow(_, q))

  private def addRow(entry: MailBoxEntry, q: Queue[Event]) = for {
    table <- findTable().some
    rt    <- ZIO.runtime[Any]
    _     <- ZIO.effectTotal {
               table.insertRow() match {
                 case tr: HTMLTableRowElement =>
                   val td1 = tr.insertCell()
                   td1.innerText = entry.subject
                   val td2 = tr.insertCell()
                   td2.innerHTML = entry.when
                   val td3 = tr.insertCell()
                   td3.innerHTML = entry.from
                   tr.onclick = { (_: MouseEvent) =>
                     rt.unsafeRunSync(q.offer(RowClickEvent(entry.id)))
                   }
                 case _                       =>
               }
             }
  } yield ()

  private def findTable(): UIO[Option[HTMLTableElement]] =
    UIO.effectTotal {
      dom.document.getElementById("inbox") match {
        case table: HTMLTableElement => Some(table)
        case _                       => None
      }
    }

  val clientLayer = MailBoxServiceClient.live(ZManagedChannel(Channels.grpcwebChannel("")))
  val appLayer    = clientLayer ++ Console.live

  def run(args: List[String]) =
    appLogic.provideLayer(appLayer).exitCode

}
