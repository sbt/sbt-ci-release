package com.geirsson

import java.io.ByteArrayInputStream
import scala.sys.process.{ProcessBuilder, ProcessLogger}
import scala.util.{Failure, Success, Try}

object PipeFail {
  implicit class PipeFailOps[R](p1: R) {
    @volatile
    private var error: Option[String] = None

    def #|!(p2: R)(implicit ev: R => ProcessBuilder): ProcessBuilder = {
      val logger = new ProcessLogger {
        override def out(s: => String): Unit = ()

        override def err(s: => String): Unit = {
          error = Some(s)
        }

        override def buffer[T](f: => T): T = f
      }
      Try(p1.!!(logger)).map(result =>
        (p2 #< new ByteArrayInputStream(result.getBytes))
      ) match {
        case Failure(exception) =>
          error match {
            case Some(errorMessageFromPipe) =>
              throw new RuntimeException(errorMessageFromPipe, exception)
            case None => throw exception
          }
        case Success(value) => value
      }
    }
  }
}
