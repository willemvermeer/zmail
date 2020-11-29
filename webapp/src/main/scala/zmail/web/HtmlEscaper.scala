package zmail.web

import zio.{ Chunk, ZIO }
import zio.stream.Stream

object HtmlEscaper {

  // escape html elements; based on https://github.com/lihaoyi/scalatags/blob/master/scalatags/src/scalatags/Escaping.scala
  def escape(s: String): ZIO[Any, Nothing, String] =
    for {
      chunk <- Stream
                 .fromChunk(Chunk.fromArray(s.toCharArray))
                 .map {
                   case '<'          => "&lt;"
                   case '>'          => "&gt;"
                   case '&'          => "&amp;"
                   case '"'          => "&quot;"
                   case '\n'         => "\n"
                   case '\r'         => ""
                   case '\t'         => ""
                   case c if c < ' ' => " "
                   case x            => x.toString
                 }
                 .runCollect
    } yield chunk.mkString

}
