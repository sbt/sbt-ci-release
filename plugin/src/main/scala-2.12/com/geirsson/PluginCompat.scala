package com.geirsson

import sbt.*

object PluginCompat {
  implicit class DefOp(singleton: Def.type) {
    def uncached[A1](a: A1): A1 = a
  }
}
