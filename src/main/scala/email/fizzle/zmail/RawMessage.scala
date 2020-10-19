package email.fizzle.zmail

import email.fizzle.zmail.Smtp.{ Domain, MailFrom, RcptTo }

case class Recipient(mailbox: String) {
  def domain = mailbox.substring(mailbox.indexOf("@") + 1)
  def localName = mailbox.substring(0, mailbox.indexOf("@"))
}
case class RawMessage(
  recipients: Seq[Recipient] = Seq(),
  domain: Option[Domain] = None,
  mailFrom: Option[MailFrom] = None,
  data: Option[String] = None
) {

  def addRecipient(recipient: Recipient) = this.copy(recipients = recipients :+ recipient)

  def addDomain(domain: Domain) = this.copy(domain = Some(domain))

  def addMailFrom(mailFrom: MailFrom) = this.copy(mailFrom = Some(mailFrom))

  def addData(data: String) = this.copy(data = Some(data))

}
