package zmail.web

import zio.test.Assertion._
import zio.test._

object HtmlEscapeZIOSpec extends DefaultRunnableSpec {

  def spec =
    suite("HtmlEscapeZIOSpec")(
      testM("HtmlEscaper changes < to &lt;") {
        for {
          cmd <- HtmlEscaper.escape("<")
        } yield assert(cmd)(equalTo("&lt;"))
      },
      testM("HtmlEscaper changes > to &gt;") {
        for {
          cmd <- HtmlEscaper.escape(">")
        } yield assert(cmd)(equalTo("&gt;"))
      },
      testM("HtmlEscaper changes double quote to &quot;") {
        for {
          cmd <- HtmlEscaper.escape("\"")
        } yield assert(cmd)(equalTo("&quot;"))
      },
      testM("HtmlEscaper changes double & to &amp;") {
        for {
          cmd <- HtmlEscaper.escape("&")
        } yield assert(cmd)(equalTo("&amp;"))
      },
      testM("HtmlEscaper keeps newlines") {
        for {
          cmd <- HtmlEscaper.escape("a\nb")
        } yield assert(cmd)(equalTo("a\nb"))
      },
      testM("HtmlEscaper escapes some gmail input") {
        val gmail        =
          """
            |<div class="gmail_default" style="font-family:tahoma,sans-serif">barely empty message<br>\n</div>
            |""".stripMargin
        val gmailEscaped =
          """
            |&lt;div class=&quot;gmail_default&quot; style=&quot;font-family:tahoma,sans-serif&quot;&gt;barely empty message&lt;br&gt;\n&lt;/div&gt;
            |""".stripMargin

        for {
          cmd <- HtmlEscaper.escape(gmail)
        } yield assert(cmd)(equalTo(gmailEscaped))
      }
    )
}
