package com.geirsson

import com.typesafe.sbt.GitPlugin
import sbtdynver.DynVerPlugin.autoImport._
import com.typesafe.sbt.SbtPgp
import com.typesafe.sbt.SbtPgp.autoImport._
import sbt.Def
import sbt._
import sbt.Keys._
import sbt.plugins.JvmPlugin
import sbtdynver.DynVerPlugin
import sys.process._
import xerial.sbt.Sonatype
import xerial.sbt.Sonatype.autoImport._

object CiReleasePlugin extends AutoPlugin {

  override def trigger = allRequirements
  override def requires =
    JvmPlugin && SbtPgp && DynVerPlugin && GitPlugin && Sonatype

  def isTravisTag: Boolean =
    Option(System.getenv("TRAVIS_TAG")).exists(_.nonEmpty)
  def isTravisSecure: Boolean =
    System.getenv("TRAVIS_SECURE_ENV_VARS") == "true"

  def setupGpg(): Unit = {
    (s"echo ${sys.env("PGP_SECRET")}" #| "base64 --decode" #| "gpg --import").!
  }

  override def buildSettings: Seq[Def.Setting[_]] = List(
    dynverSonatypeSnapshots := true,
    pgpPassphrase := sys.env.get("PGP_PASSPHRASE").map(_.toCharArray())
  )

  override def globalSettings: Seq[Def.Setting[_]] = List(
    publishArtifact.in(Test) := false,
    publishMavenStyle := true,
    commands += Command.command("ci-release") { s =>
      if (!isTravisSecure) {
        println("No access to secret variables, doing nothing")
        s
      } else {
        val tag = sys.env("TRAVIS_TAG").trim
        println(
          s"Running ci-release.\n" +
            s"  TRAVIS_SECURE_ENV_VARS=${sys.env("TRAVIS_SECURE_ENV_VARS")}\n" +
            s"  TRAVIS_BRANCH=${sys.env("TRAVIS_BRANCH")}\n" +
            s"  TRAVIS_TAG=${tag}"
        )
        setupGpg()
        if (!isTravisTag) {
          println(s"No tag push, publishing SNAPSHOT")
          sys.env.getOrElse("CI_SNAPSHOT_RELEASE", "+publish") ::
            s
        } else {
          println("Tag push detected, publishing a stable release")
          s"sonatypeOpen $tag" ::
            sys.env.getOrElse("CI_RELEASE", "+publishSigned") ::
            s"sonatypeRelease $tag" ::
            s
        }
      }
    }
  )

  override def projectSettings: Seq[Def.Setting[_]] = List(
    publishTo := sonatypePublishTo.value
  )

}
