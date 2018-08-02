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
    commands += Command.command("ci-release") { currentState =>
      if (!isTravisSecure) {
        println("No access to secret variables, doing nothing")
        currentState
      } else {
        println(
          s"Running ci-release.\n" +
            s"  TRAVIS_SECURE_ENV_VARS=${sys.env("TRAVIS_SECURE_ENV_VARS")}\n" +
            s"  TRAVIS_BRANCH=${sys.env("TRAVIS_BRANCH")}\n" +
            s"  TRAVIS_TAG=${sys.env("TRAVIS_TAG")}"
        )
        setupGpg()
        if (!isTravisTag) {
          if (isSnapshotVersion(currentState)) {
            println(s"No tag push, publishing SNAPSHOT")
            sys.env.getOrElse("CI_SNAPSHOT_RELEASE", "+publish") ::
              currentState
          } else {
            // Happens when a tag is pushed right after merge causing the master branch
            // job to pick up a non-SNAPSHOT version even if TRAVIS_TAG=false.
            println(
              "Snapshot releases must have -SNAPSHOT version number, doing nothing"
            )
            currentState
          }
        } else {
          println("Tag push detected, publishing a stable release")
          sys.env.getOrElse("CI_RELEASE", "+publishSigned") ::
            s"sonatypeRelease" ::
            currentState
        }
      }
    }
  )

  override def projectSettings: Seq[Def.Setting[_]] = List(
    publishTo := sonatypePublishTo.value
  )

  def isSnapshotVersion(state: State): Boolean = {
    version.in(ThisBuild).get(Project.extract(state).structure.data) match {
      case Some(v) => v.endsWith("-SNAPSHOT")
      case None    => throw new NoSuchFieldError("version")
    }
  }

}
