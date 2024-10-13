package com.geirsson

import CiReleasePlugin.{ backPubVersionToCommand, dropBackPubCommand }

class CiReleaseTest extends munit.FunSuite {
  val expectedVer = "1.1.0"

  test("Normal version default") {
    assertEquals(backPubVersionToCommand("1.1.0"), "+publishSigned")
    assertEquals(dropBackPubCommand("1.1.0"), expectedVer)
  }

  test("Command starting with number is assumed to be a cross version") {
    assertEquals(backPubVersionToCommand("1.1.0@2.12.20"), ";++2.12.20!;publishSigned")
    assertEquals(dropBackPubCommand("1.1.0@2.12.20"), expectedVer)

    assertEquals(backPubVersionToCommand("1.1.0@3.x"), ";++3.x;publishSigned")
    assertEquals(dropBackPubCommand("1.1.0@3.x"), expectedVer)
  }

  test("Non-number is treated as an alternative publish command") {
    assertEquals(backPubVersionToCommand("1.1.0@foo/publishSigned"), "foo/publishSigned")
    assertEquals(dropBackPubCommand("1.1.0@foo/publishSigned"), expectedVer)

    assertEquals(backPubVersionToCommand("1.1.0@+foo/publishSigned"), "+foo/publishSigned")
    assertEquals(dropBackPubCommand("1.1.0@+foo/publishSigned"), expectedVer)
  }

  test("Commands can be chained") {
    assertEquals(backPubVersionToCommand("1.1.0@2.12.20@foo/publishSigned"), ";++2.12.20!;foo/publishSigned")
    assertEquals(dropBackPubCommand("1.1.0@2.12.20@foo/publishSigned"), expectedVer)

    assertEquals(backPubVersionToCommand("1.1.0@foo/something@bar/publishSigned"), ";foo/something;bar/publishSigned")
    assertEquals(dropBackPubCommand("1.1.0@foo/something@bar/publishSigned"), expectedVer)
  }

  test("Treat # as comments") {
    assertEquals(backPubVersionToCommand("1.1.0#comment"), "+publishSigned")
    assertEquals(dropBackPubCommand("1.1.0#comment"), expectedVer)

    assertEquals(backPubVersionToCommand("1.1.0@2.12.20#comment"), ";++2.12.20!;publishSigned")
    assertEquals(dropBackPubCommand("1.1.0@2.12.20#comment"), expectedVer)

    assertEquals(backPubVersionToCommand("1.1.0@3.x#comment"), ";++3.x;publishSigned")
    assertEquals(dropBackPubCommand("1.1.0@3.x#comment"), expectedVer)
  }
}
