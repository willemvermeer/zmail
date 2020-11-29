package zmail.smtp

sealed trait ReplyCode {
  val code: Int
}
object ReplyCode       {
  case object READY    extends ReplyCode { val code = 220 }
  case object CLOSING  extends ReplyCode { val code = 221 }
  case object OK       extends ReplyCode { val code = 250 }
  case object START    extends ReplyCode { val code = 354 }
  case object REJECTED extends ReplyCode { val code = 550 }
}
