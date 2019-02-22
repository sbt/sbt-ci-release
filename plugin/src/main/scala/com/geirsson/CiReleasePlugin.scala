package com.geirsson

import com.typesafe.sbt.GitPlugin
import com.typesafe.sbt.SbtPgp
import com.typesafe.sbt.SbtPgp.autoImport._
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Base64
import sbt.Def
import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin
import sbtdynver.DynVerPlugin
import sbtdynver.DynVerPlugin.autoImport._
import scala.sys.process._
import xerial.sbt.Sonatype
import xerial.sbt.Sonatype.autoImport._

object CiReleasePlugin extends AutoPlugin {

  override def trigger = allRequirements
  override def requires =
    JvmPlugin && SbtPgp && DynVerPlugin && GitPlugin && Sonatype

  def isTravisTag: Boolean =
    Option(System.getenv("TRAVIS_TAG")).exists(_.nonEmpty) ||
      Option(System.getenv("BUILD_SOURCEBRANCH"))
        .exists(_.startsWith("refs/tags"))
  def isTravisSecure: Boolean =
    System.getenv("TRAVIS_SECURE_ENV_VARS") == "true" ||
      System.getenv("BUILD_REASON") == "IndividualCI"
  def travisTag: String =
    Option(System.getenv("TRAVIS_TAG"))
      .orElse(Option(System.getenv("BUILD_SOURCEBRANCH")))
      .getOrElse("<unknown>")
  def travisBranch: String =
    Option(System.getenv("TRAVIS_BRANCH"))
      .orElse(Option(System.getenv("BUILD_SOURCEBRANCH")))
      .getOrElse("<unknown>")
  def isAzure: Boolean =
    System.getenv("TF_BUILD") == "True"

  def setupGpg(): Unit = {
    val secret = sys.env("PGP_SECRET")
    if (isAzure) {
      Files.write(Paths.get("gpg.zip"), Base64.getDecoder.decode(secret))
      s"unzip gpg.zip".!
      "gpg --import gpg.key".!
    } else {
      (s"echo ${secret}" #| "base64 --decode" #| "gpg --import").!
    }
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
            s"  TRAVIS_SECURE_ENV_VARS=${isTravisSecure}\n" +
            s"  TRAVIS_BRANCH=${travisBranch}\n" +
            s"  TRAVIS_TAG=${travisTag}"
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
    publishConfiguration :=
      publishConfiguration.value.withOverwrite(true),
    publishLocalConfiguration :=
      publishLocalConfiguration.value.withOverwrite(true),
    publishTo := sonatypePublishTo.value
  )

  def isSnapshotVersion(state: State): Boolean = {
    version.in(ThisBuild).get(Project.extract(state).structure.data) match {
      case Some(v) => v.endsWith("-SNAPSHOT")
      case None    => throw new NoSuchFieldError("version")
    }
  }

}
