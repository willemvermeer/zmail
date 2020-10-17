package email.fizzle.zmail

sealed abstract class ReplyCode(val code: Int)
object ReplyCode {
  case object READY    extends ReplyCode(220)
  case object CLOSING  extends ReplyCode(221)
  case object OK       extends ReplyCode(250)
  case object START    extends ReplyCode(354)
  case object REJECTED extends ReplyCode(550)
}
