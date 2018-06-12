package com.geirsson

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

  def setupGpg(): Unit = {
    (s"echo ${sys.env("PGP_SECRET")}" #| "base64 --decode" #| "gpg --import").!
  }

  override def buildSettings: Seq[Def.Setting[_]] = List(
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
            s"  TRAVIS_SECURE_ENV_VARS=${sys.env("TRAVIS_SECURE_ENV_VARS")}\n" +
            s"  TRAVIS_BRANCH=${sys.env("TRAVIS_BRANCH")}\n" +
            s"  TRAVIS_TAG=${sys.env("TRAVIS_TAG")}"
        )
        setupGpg()
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
