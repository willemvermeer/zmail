package email.fizzle.zmail

import email.fizzle.zmail.Smtp.{ Domain, Ehlo, Helo, MailFrom, ReversePath }
import fastparse.Parsed
import org.scalatest._
import flatspec._
import matchers._

class SmtpParserSpec extends AnyFlatSpec with should.Matchers {

  "The SmtpParser" should "parse willem" in {
    fastparse.parse("willem", SmtpParser.willem(_)) shouldBe Parsed.Success((), 6)
  }
  "The SmtpParser" should "parse HELO smtp.afzender.nl" in {
    fastparse.parse("HELO smtp.afzender.nl", SmtpParser.helo(_)) shouldBe Parsed.Success(
      Helo(Domain("smtp.afzender.nl")),
      21
    )
  }
  "The SmtpParser" should "parse EHLO willems-mbp-2.fritz.box" in {
    println(fastparse.parse("w", SmtpParser.letdig(_)))
    println(fastparse.parse("ww", SmtpParser.letdig(_)))
    println(fastparse.parse("*", SmtpParser.letdig(_)))
    println(fastparse.parse("1", SmtpParser.letdig(_)))
    println(fastparse.parse("12", SmtpParser.letdig(_)))
    println(fastparse.parse("EHLO willems-mbp-2.fritz.box", SmtpParser.ehlo(_)))
    println(fastparse.parse("willems-mbp-2.fritz.box", SmtpParser.subdomain(_)))
    println(fastparse.parse("EHLO w2illemsmbp-2.fritz.box", SmtpParser.ehlo(_)))
    println(fastparse.parse("EHLO willems-mbp2.fritz.box", SmtpParser.ehlo(_)))
    println(fastparse.parse("EHLO willemsmbp2.fritz.box", SmtpParser.ehlo(_)))
    fastparse.parse("EHLO willems-mbp-2.fritz.box", SmtpParser.ehlo(_)) shouldBe Parsed.Success(
      Ehlo(Domain("willems-mbp-2.fritz.box")),
      28
    )
  }
  "The SmtpParser" should "parse MAIL FROM:<sonoojaiswal1987@gmail.com>" in {
    println(fastparse.parse("a@b.c", SmtpParser.mailbox(_)))
    println(fastparse.parse("aa@bb.com", SmtpParser.mailbox(_)))
    println(fastparse.parse("aa.ee@bb.com", SmtpParser.mailbox(_)))
    println(fastparse.parse("<aa.ee@bb.com>", SmtpParser.reversepath(_)))
    println(fastparse.parse("<sonoojaiswal1987@gmail.com>", SmtpParser.reversepath(_)))
    fastparse.parse("MAIL FROM:<sonoojaiswal1987@gmail.com>", SmtpParser.mailfrom(_)) shouldBe Parsed.Success(
      MailFrom(ReversePath("sonoojaisswal1987@gmail.com")),
      28
    )
  }
  "The SmtpParser" should "not parse vermeer" in {
    fastparse.parse("vermeer", SmtpParser.willem(_)) shouldBe a[Parsed.Failure]
  }
}
