package email.fizzle.zmail
import email.fizzle.zmail.Smtp._
import fastparse.NoWhitespace._
import fastparse._
import zio.{ IO, ZIO }

object SmtpParser {

  def willem[_: P]: P[Unit] = P("willem")

  def EOL[_: P]: P[Unit] = P( "\r" ~ "\n" )

  // see https://tools.ietf.org/html/rfc5321#section-4.1.2
  def digit[_: P]: P[Unit] = P( CharIn("0-9") )
  def alpha[_: P]: P[Unit] = P( CharIn("a-z") | CharIn("A-Z") )
  def letters[_: P]: P[Unit] = P( alpha.rep(1) )
  def letdig[_: P]: P[String] = P( (alpha | digit).! )
  def prascii[_: P]: P[Unit] = P( "!" | "#" |  "$" | "%" | "&" | "'" | "*" | "+" | "-" | "/" | "=" | "?" | "^" | "_" | "`" | "{" | "|" | "}" | "~" )
  def atext[_: P]: P[Unit] = P( alpha | digit | prascii)
  def atom[_: P]: P[Unit] = P( ( alpha | digit | prascii ).rep(1) )
  def dotstring[_: P]: P[Unit] = P( atom ~ ( "." ~ atom ).rep(0))

  def ldhstr[_: P]: P[Unit] = P( ( letdig | "-" ).rep(0) ~ letdig )
  def subdomain[_: P]: P[Unit] = P( letdig ~ ldhstr.? )
  def domain[_: P]: P[Unit] = P( subdomain ~ ( "." ~ subdomain).rep(0) )

  def qtextsmtp[_: P]: P[Unit] = P( " " | "!" | CharIn("#-[") | CharIn("]-~") ) // bnf: %d32-33 / %d35-91 / %d93-126
  def quotedpairsmtp[_: P]: P[Unit] = P( "\\" ~ CharIn(" -~") ) // bnf: %d92 %d32-126
  def qcontentsmtp[_: P]: P[Unit] = P( qtextsmtp | quotedpairsmtp )
  def quotedstring[_: P]: P[Unit] = P( "\"" ~ qcontentsmtp.rep(0) ~ "\"" )

  def localpart[_: P]: P[Unit] = P( dotstring | quotedstring )
  def snum[_: P]: P[Unit] = P( digit.rep(min = 1, max = 3) )
  def ipv4addressliteral[_: P]: P[Unit] = P( snum ~ ( "." ~ snum).rep(exactly = 3) )
  // TODO: ipv6 support
  def dcontent[_: P]: P[Unit] = P( CharIn("!-Z") | CharIn("^-~") ) // bnf: %d33-90 / %d94-126
  def standardizedtag[_: P]: P[Unit] = ldhstr
  def generaladdressliteral[_: P]: P[Unit] = P( standardizedtag ~ ":" ~ dcontent.rep(1) )
  def addressliteral[_: P]: P[Unit] = P( "[" ~ ( ipv4addressliteral | generaladdressliteral ) ~ "]" )

  def domainC[_: P]: P[Domain] = domain.!.map(Domain)

  def mailbox[_: P]: P[Unit] = P( localpart ~ "@" ~ (domain | addressliteral) )
  def atdomain[_: P]: P[Unit] = P( "@" ~ domain )
  def adl[_: P]: P[Unit] = P( atdomain ~ ( "," ~ atdomain ).rep(0)) // only the first @domain will be picked up
  def path[_: P]: P[String] = P( "<" ~ ( adl ~ ":" ).? ~ mailbox.! ~ ">" )
  def forwardpath[_: P]: P[String] = P( path )
  def reversepath[_: P]: P[String] = P( path | "<>" ).map(_.toString)

  def esmtpvalue[_: P]: P[Unit] = P( (CharIn("!-<") | CharIn(">-~")).rep(1) ) // bnf 1*(%d33-60 / %d62-126)
  def esmtpkeyword[_: P]: P[Unit] = P( letdig ~ ( letdig | "-" ).rep(0) )
  def esmtpparam[_: P]: P[Unit] = P( esmtpkeyword ~ ( "=" ~ esmtpvalue ).? )
  def mailparameters[_: P]: P[Unit] = P( esmtpparam ~ ( " " ~ esmtpparam ).rep(0) )

  def helo[_: P]: P[Helo] = P( "HELO" ~ " " ~ domainC ).map(Helo)
  def ehlo[_: P]: P[Ehlo] = P( "EHLO" ~ " " ~ domainC ).map(Ehlo)
  def mailfrom[_: P]: P[MailFrom] = P( "MAIL FROM:" ~ reversepath ~ (" " ~ mailparameters ).? ).map(p => MailFrom(ReversePath(p)))
  def rcptto[_: P]: P[RcptTo] = P( "RCPT TO:" ~ forwardpath ~ (" " ~ mailparameters ).? ).map(p => RcptTo(ReversePath(p)))
  def quit[_: P]: P[Quit.type] = P( "QUIT" ).map(_ => Quit)

  def command[_: P]: P[Command] = P( ( helo | ehlo | mailfrom | rcptto | quit) ~ EOL )

  def parse(line: String): IO[ParseError, Command] = fastparse.parse(line, SmtpParser.command(_)) match {
    case Parsed.Success(cmd, _) => ZIO.succeed(cmd)
    case Parsed.Failure(label, index, _) => ZIO.fail(ParseError(s"ParserError $label at $index for $line"))
  }

  case class ParseError(msg: String) extends Exception(msg)

}
object Smtp {

  case class Domain(domain: String)
  case class ReversePath(path: String)

  sealed trait Command
  case class Helo(domain: Domain) extends Command
  case class Ehlo(domain: Domain) extends Command
  case class MailFrom(path: ReversePath) extends Command
  case class RcptTo(path: ReversePath) extends Command
  case object Data extends Command
  case object Quit extends Command
}