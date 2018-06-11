package com.geirsson

import com.typesafe.sbt.pgp.PgpKeys
import sbtdynver.DynVerPlugin.autoImport._
import sbt.Def
import sbt._
import sbt.Keys._
import sbt.plugins.JvmPlugin
import sys.process._

object CiReleasePlugin extends AutoPlugin {

  override def trigger = allRequirements
  override def requires = JvmPlugin

  def isTravisTag: Boolean =
    Option(System.getenv("TRAVIS_TAG")).exists(_.nonEmpty)
  def isTravisSecure: Boolean =
    System.getenv("TRAVIS_SECURE_ENV_VARS") == "true"

  private def env(key: String): String =
    Option(System.getenv(key)).getOrElse {
      throw new NoSuchElementException(key)
    }

  override def buildSettings: Seq[Def.Setting[_]] = List(
    PgpKeys.pgpPassphrase := sys.env.get("PGP_PASSPHRASE").map(_.toCharArray()),
    dynverSonatypeSnapshots := true
  )

  override def globalSettings: Seq[Def.Setting[_]] = List(
    publishMavenStyle := true,
    commands += Command.command("ci-release") { s =>
      if (!isTravisSecure) {
        println("No access to secret variables, doing nothing")
        s
      } else {
        println(
          s"Running ci-release.\n" +
            s"  TRAVIS_SECURE_ENV_VARS=${env("TRAVIS_SECURE_ENV_VARS")}\n" +
            s"  TRAVIS_BRANCH=${env("TRAVIS_BRANCH")}\n" +
            s"  TRAVIS_TAG=${env("TRAVIS_TAG")}"
        )
        println("Setting up gpg")
        (s"echo ${env("PGP_SECRET")}" #| "base64 --decode" #| "gpg --import").!
        if (!isTravisTag) {
          println(s"No tag push, publishing SNAPSHOT")
          "+publish" ::
            s
        } else {
          println("Tag push detected, publishing a stable release")
          "+publishSigned" ::
            "sonatypeReleaseAll" ::
            s
        }
      }
    }
  )

  override def projectSettings: Seq[Def.Setting[_]] = List(
    publishTo := Some {
      if (isTravisTag) Opts.resolver.sonatypeStaging
      else Opts.resolver.sonatypeSnapshots
    }
  )

}
