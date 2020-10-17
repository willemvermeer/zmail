package email.fizzle.zmail

import email.fizzle.zmail.Smtp.{ Domain, Ehlo }
import zio.test.Assertion._
import zio.test._

object SmtpParserZIOSpec extends DefaultRunnableSpec {

  def spec =
    suite("SmtpParserZIOSpec")(
      testM("SmtpParser parses EHLO with domain") {
        for {
          cmd <- SmtpParser.parse("EHLO willems-mbp-2.fritz.box\r\n")
        } yield assert(cmd)(equalTo(Ehlo(Domain("willems-mbp-2.fritz.box"))))
      }
    )
}
