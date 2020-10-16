package email.fizzle.zmail

import email.fizzle.zmail.Smtp.{ Domain, MailFrom, RcptTo }

case class Recipient(mailbox: String) // for simplicity the mailbox is just a String
case class RawMessage(recipients: Seq[Recipient] = Seq(), domain: Option[Domain] = None,
                      mailFrom: Option[MailFrom] = None) {

  def addRecipient(recipient: Recipient) = this.copy(recipients = recipients :+ recipient)

  def addDomain(domain: Domain) = this.copy(domain = Some(domain))

  def addMailFrom(mailFrom: MailFrom) = this.copy(mailFrom = Some(mailFrom))

}
