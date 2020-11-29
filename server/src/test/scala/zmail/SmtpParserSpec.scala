package zmail

import zmail.smtp.Smtp.{ Domain, Ehlo, Helo, MailFrom, ReversePath }
import zmail.smtp.SmtpParser
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
  "The SmtpParser" should "successfully parse single letter-digit combinations" in {
    Seq("w", "ww", "1", "12").map(testCase => fastparse.parse(testCase, SmtpParser.letdig(_))) shouldBe
      Seq(Parsed.Success("w", 1), Parsed.Success("w", 1), Parsed.Success("1", 1), Parsed.Success("1", 1))
  }
  "The SmtpParser" should "fail to parse single non letter-digit combinations" in {
    fastparse.parse("*", SmtpParser.letdig(_)).isSuccess shouldBe false
    fastparse.parse("@", SmtpParser.letdig(_)).isSuccess shouldBe false
    fastparse.parse(".", SmtpParser.letdig(_)).isSuccess shouldBe false
  }
  "The SmtpParser" should "parse EHLO willems-mbp-2.fritz.box" in {
    val inputs =
      Seq("willems-mbp-2.fritz.box", "w2illemsmbp-2.fritz.box", "willems-mbp2.fritz.box", "willemsmbp2.fritz.box")
    inputs
      .map(testCase => (testCase, fastparse.parse("EHLO " + testCase, SmtpParser.ehlo(_)).get))
      .map(result => result._2 == Parsed.Success(Ehlo(Domain(result._1)), result._1.length + 5))
      .fold[Boolean](true) { case (a, b) => a && b } shouldBe true
  }
  "The SmtpParser" should "parse MAIL FROM:<test@example.org>" in {
    fastparse.parse("a@b.c", SmtpParser.mailbox(_)).get shouldBe Parsed.Success((), 5)
    fastparse.parse("aa@bb.com", SmtpParser.mailbox(_)).get shouldBe Parsed.Success((), 9)
    fastparse.parse("aa.ee@bb.com", SmtpParser.mailbox(_)).get shouldBe Parsed.Success((), 12)
    fastparse.parse("<aa.ee@bb.com>", SmtpParser.reversepath(_)).get shouldBe Parsed.Success("aa.ee@bb.com", 14)
    fastparse.parse("<test@example.org>", SmtpParser.reversepath(_)).get shouldBe Parsed.Success("test@example.org", 18)
    fastparse.parse("MAIL FROM:<test@example.org>", SmtpParser.mailfrom(_)) shouldBe Parsed.Success(
      MailFrom(ReversePath("test@example.org")),
      28
    )
  }
  "The SmtpParser" should "not parse vermeer" in {
    fastparse.parse("vermeer", SmtpParser.willem(_)) shouldBe a[Parsed.Failure]
  }
}
