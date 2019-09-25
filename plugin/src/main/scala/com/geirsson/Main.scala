package com.geirsson

object Main {
  def main(args: Array[String]): Unit = {
    println(s"Hello ${args.headOption.getOrElse("world")}!")
  }
}
