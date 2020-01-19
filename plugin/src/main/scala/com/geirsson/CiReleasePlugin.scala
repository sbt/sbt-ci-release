package com.geirsson

import com.typesafe.sbt.GitPlugin
import com.typesafe.sbt.SbtGit.GitKeys
import com.jsuereth.sbtpgp.SbtPgp
import com.jsuereth.sbtpgp.SbtPgp.autoImport._
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.charset.StandardCharsets
import java.util.Base64
import sbt.Def
import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin
import sbtdynver.DynVerPlugin
import sbtdynver.DynVerPlugin.autoImport._
import scala.deprecated
import scala.sys.process._
import scala.util.control.NonFatal
import xerial.sbt.Sonatype
import xerial.sbt.Sonatype.autoImport._

object CiReleasePlugin extends AutoPlugin {

  override def trigger = allRequirements
  override def requires =
    JvmPlugin && SbtPgp && DynVerPlugin && GitPlugin && Sonatype

  def isSecure: Boolean =
    System.getenv("TRAVIS_SECURE_ENV_VARS") == "true" ||
      System.getenv("BUILD_REASON") == "IndividualCI" ||
      System.getenv("PGP_SECRET") != null
  def isTag: Boolean =
    Option(System.getenv("TRAVIS_TAG")).exists(_.nonEmpty) ||
      Option(System.getenv("CIRCLE_TAG")).exists(_.nonEmpty) ||
      Option(System.getenv("BUILD_SOURCEBRANCH"))
        .orElse(Option(System.getenv("GITHUB_REF")))
        .exists(_.startsWith("refs/tags"))
  def releaseTag: String =
    Option(System.getenv("TRAVIS_TAG"))
      .orElse(Option(System.getenv("BUILD_SOURCEBRANCH")))
      .orElse(Option(System.getenv("GITHUB_REF")))
      .orElse(Option(System.getenv("CIRCLE_TAG")))
      .getOrElse("<unknown>")
  def currentBranch: String =
    Option(System.getenv("TRAVIS_BRANCH"))
      .orElse(Option(System.getenv("BUILD_SOURCEBRANCH")))
      .orElse(Option(System.getenv("GITHUB_REF")))
      .orElse(Option(System.getenv("CIRCLE_BRANCH")))
      .getOrElse("<unknown>")

  @deprecated("Deprecated, please use isSecure", "1.4.32")
  def isTravisSecure: Boolean = isSecure
  @deprecated("Deprecated, please use isTag", "1.4.32")
  def isTravisTag: Boolean = isTag
  @deprecated("Deprecated, please use releaseTag", "1.4.32")
  def travisTag: String = releaseTag
  @deprecated("Deprecated, please use currentBranch", "1.4.32")
  def travisBranch: String = currentBranch

  def isAzure: Boolean =
    System.getenv("TF_BUILD") == "True"
  def isGithub: Boolean =
    System.getenv("GITHUB_ACTION") != null
  def isCircleCi: Boolean =
    System.getenv("CIRCLECI") == true

  def setupGpg(): Unit = {
    List("gpg", "--version").!
    val secret = sys.env("PGP_SECRET")
    if (isAzure) {
      // base64 encoded gpg secrets are too large for Azure variables but
      // they fit within the 4k limit when compressed.
      Files.write(Paths.get("gpg.zip"), Base64.getDecoder.decode(secret))
      s"unzip gpg.zip".!
      s"gpg --import gpg.key".!
    } else {
      (s"echo $secret" #| "base64 --decode" #| "gpg --import").!
    }
  }

  override lazy val buildSettings: Seq[Def.Setting[_]] = List(
    dynverSonatypeSnapshots := true,
    scmInfo ~= {
      case Some(info) => Some(info)
      case None =>
        import scala.sys.process._
        val identifier = """([^\/]+)"""
        val GitHubHttps = s"https://github.com/$identifier/$identifier".r
        try {
          val remote = List("git", "ls-remote", "--get-url", "origin").!!.trim()
          remote match {
            case GitHubHttps(user, repo) =>
              Some(
                ScmInfo(
                  url(s"https://github.com/$user/$repo"),
                  s"scm:git:https://github.com/$user/$repo.git",
                  Some(s"scm:git:git@github.com:$user/$repo.git")
                )
              )
            case _ =>
              None
          }
        } catch {
          case NonFatal(_) => None
        }
    }
  )

  override lazy val globalSettings: Seq[Def.Setting[_]] = List(
    publishArtifact.in(Test) := false,
    publishMavenStyle := true,
    commands += Command.command("ci-release") { currentState =>
      if (!isSecure) {
        println("No access to secret variables, doing nothing")
        currentState
      } else {
        println(
          s"Running ci-release.\n" +
            s"  branch=$currentBranch"
        )
        setupGpg()
        // https://github.com/olafurpg/sbt-ci-release/issues/64
        val reloadKeyFiles =
          "; set pgpSecretRing := pgpSecretRing.value; set pgpPublicRing := pgpPublicRing.value"
        if (!isTag) {
          if (isSnapshotVersion(currentState)) {
            println(s"No tag push, publishing SNAPSHOT")
            reloadKeyFiles ::
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
          reloadKeyFiles ::
            sys.env.getOrElse("CI_RELEASE", "+publishSigned") ::
            sys.env.getOrElse(
              "CI_SONATYPE_RELEASE",
              "; sonatypeBundleClean ; sonatypeBundleRelease"
            ) ::
            currentState
        }
      }
    }
  )

  override lazy val projectSettings: Seq[Def.Setting[_]] = List(
    publishConfiguration :=
      publishConfiguration.value.withOverwrite(true),
    publishLocalConfiguration :=
      publishLocalConfiguration.value.withOverwrite(true),
    publishTo := sonatypePublishToBundle.value
  )

  def isSnapshotVersion(state: State): Boolean = {
    version.in(ThisBuild).get(Project.extract(state).structure.data) match {
      case Some(v) => v.endsWith("-SNAPSHOT")
      case None    => throw new NoSuchFieldError("version")
    }
  }
}
