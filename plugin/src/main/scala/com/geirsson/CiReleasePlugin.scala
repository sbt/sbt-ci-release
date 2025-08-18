package com.geirsson

import com.geirsson.PipeFail.PipeFailOps
import PluginCompat.*
import com.github.sbt.git.GitPlugin
import com.github.sbt.git.SbtGit.GitKeys
import com.jsuereth.sbtpgp.SbtPgp
import com.jsuereth.sbtpgp.SbtPgp.autoImport.*

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.charset.StandardCharsets
import java.util.Base64
import sbt.Def
import sbt.Keys.*
import sbt.{given, *}
import sbt.plugins.JvmPlugin
import sbtdynver.DynVerPlugin
import sbtdynver.DynVerPlugin.autoImport.*

import scala.deprecated
import scala.sys.process.{given, *}
import scala.util.control.NonFatal

object CiReleasePlugin extends AutoPlugin {

  override def trigger = allRequirements
  override def requires =
    JvmPlugin && SbtPgp && DynVerPlugin && GitPlugin

  def isSecure: Boolean =
    System.getenv("TRAVIS_SECURE_ENV_VARS") == "true" ||
      System.getenv("BUILD_REASON") == "IndividualCI" ||
      System.getenv("PGP_SECRET") != null
  def isTag: Boolean =
    Option(System.getenv("TRAVIS_TAG")).exists(_.nonEmpty) ||
      Option(System.getenv("CIRCLE_TAG")).exists(_.nonEmpty) ||
      Option(System.getenv("CI_COMMIT_TAG")).exists(_.nonEmpty) ||
      Option(System.getenv("BUILD_SOURCEBRANCH"))
        .orElse(Option(System.getenv("GITHUB_REF")))
        .exists(_.startsWith("refs/tags"))
  def releaseTag: String =
    Option(System.getenv("TRAVIS_TAG"))
      .orElse(Option(System.getenv("CIRCLE_TAG")))
      .orElse(Option(System.getenv("CI_COMMIT_TAG")))
      .orElse(Option(System.getenv("BUILD_SOURCEBRANCH")))
      .orElse(Option(System.getenv("GITHUB_REF")))
      .getOrElse("<unknown>")
  def currentBranch: String =
    Option(System.getenv("TRAVIS_BRANCH"))
      .orElse(Option(System.getenv("CIRCLE_BRANCH")))
      .orElse(Option(System.getenv("CI_COMMIT_BRANCH")))
      .orElse(Option(System.getenv("BUILD_SOURCEBRANCH")))
      .orElse(Option(System.getenv("GITHUB_REF")))
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
    System.getenv("CIRCLECI") == "true"
  def isGitlab: Boolean =
    System.getenv("GITLAB_CI") == "true"

  def setupGpg(): Unit = {
    val versionLine = List("gpg", "--version").!!.linesIterator.toList.head
    println(versionLine)
    val TaggedVersion = """(\d{1,14})([\.\d{1,14}]*)((?:-\w+)*)""".r
    val gpgVersion: Long = versionLine.split(" ").last match {
      case TaggedVersion(m, _, _) => m.toLong
      case _                      => 0L
    }
    // https://dev.gnupg.org/T2313
    val importCommand =
      if (gpgVersion < 2L) "--import"
      else "--batch --import"
    val secret = sys.env("PGP_SECRET")
    if (isAzure) {
      // base64 encoded gpg secrets are too large for Azure variables but
      // they fit within the 4k limit when compressed.
      Files.write(Paths.get("gpg.zip"), Base64.getDecoder.decode(secret))
      s"unzip gpg.zip".!
      s"gpg $importCommand gpg.key".!
    } else {
      (Process(s"echo $secret") #|!
        Process("base64 --decode") #|!
        Process(s"gpg $importCommand")).!
    }
  }

  private def gitHubScmInfo(user: String, repo: String) =
    ScmInfo(
      url(s"https://github.com/$user/$repo"),
      s"scm:git:https://github.com/$user/$repo.git",
      Some(s"scm:git:git@github.com:$user/$repo.git")
    )

  lazy val cireleasePublishCommand = settingKey[String]("")

  // copied from sbt Keys.scala
  private val localStaging = settingKey[Option[Resolver]](
    "Local staging resolver for Sonatype publishing"
  )
  private val sbtPluginPublishLegacyMavenStyle = settingKey[Boolean](
    "Configuration for generating the legacy pom of sbt plugins, to publish to Maven"
  )
  override lazy val buildSettings: Seq[Def.Setting[?]] = List(
    dynverSonatypeSnapshots := true,
    // Central Portal no longer supports the legacy style
    sbtPluginPublishLegacyMavenStyle := false,
    scmInfo ~= {
      case Some(info) => Some(info)
      case None       =>
        import scala.sys.process.*
        val identifier = """([^\/]+?)"""
        val GitHubHttps =
          s"https://github.com/$identifier/$identifier(?:\\.git)?".r
        val GitHubGit = s"git://github.com:$identifier/$identifier(?:\\.git)?".r
        val GitHubSsh = s"git@github.com:$identifier/$identifier(?:\\.git)?".r
        try {
          val remote = List("git", "ls-remote", "--get-url", "origin").!!.trim()
          remote match {
            case GitHubHttps(user, repo) => Some(gitHubScmInfo(user, repo))
            case GitHubGit(user, repo)   => Some(gitHubScmInfo(user, repo))
            case GitHubSsh(user, repo)   => Some(gitHubScmInfo(user, repo))
            case _                       => None
          }
        } catch {
          case NonFatal(_) => None
        }
    },
    cireleasePublishCommand := {
      val gitDescribe = dynverGitDescribeOutput.value
      val v = gitDescribe.getVersion(
        dynverCurrentDate.value,
        dynverSeparator.value,
        dynverSonatypeSnapshots.value
      )
      sys.env.get("CI_RELEASE") match {
        case Some(cmd) => cmd
        case None      => backPubVersionToCommand(v)
      }
    },
    version := {
      val v = version.value
      dynverGitDescribeOutput.value match {
        case Some(gitDescribe) =>
          val tagVersion = gitDescribe.ref.dropPrefix
          if (gitDescribe.isCleanAfterTag) {
            dropBackPubCommand(v)
          } else if (v.startsWith(tagVersion)) {
            dropBackPubCommand(tagVersion) + v.drop(tagVersion.size)
          } else v
        case _ => v
      }
    }
  )

  override lazy val globalSettings: Seq[Def.Setting[?]] = List(
    (Test / publishArtifact) := false,
    publishMavenStyle := true,
    commands += Command.command("ci-release") { currentState =>
      val version = getVersion(currentState)
      val isSnapshot = isSnapshotVersion(version)
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

        val publishCommand = getPublishCommand(currentState)

        if (!isTag) {
          if (isSnapshot) {
            println(s"No tag push, publishing SNAPSHOT")
            reloadKeyFiles ::
              sys.env.getOrElse("CI_CLEAN", "; clean") ::
              // workaround for *.asc.sha1 not being allowed
              sys.env.getOrElse("CI_SNAPSHOT_RELEASE", "+publish") ::
              currentState
          } else {
            // Happens when a tag is pushed right after merge causing the main branch
            // job to pick up a non-SNAPSHOT version even if TRAVIS_TAG=false.
            println(
              "Snapshot releases must have -SNAPSHOT version number, doing nothing"
            )
            currentState
          }
        } else {
          println("Tag push detected, publishing a stable release")
          reloadKeyFiles ::
            sys.env.getOrElse("CI_CLEAN", "; clean") ::
            publishCommand ::
            sys.env.getOrElse("CI_SONATYPE_RELEASE", "sonaRelease") ::
            currentState
        }
      }
    }
  )

  override lazy val projectSettings: Seq[Def.Setting[?]] = List(
    version := (ThisBuild / version).value,
    publishConfiguration :=
      Def.uncached(publishConfiguration.value.withOverwrite(true)),
    publishLocalConfiguration :=
      Def.uncached(publishLocalConfiguration.value.withOverwrite(true)),
    publishTo := {
      val orig = (ThisBuild / publishTo).value
      (orig, localStaging.?.value) match {
        case (Some(r), _)          => orig
        case (None, None)          => orig
        case (None, Some(staging)) =>
          val centralSnapshots =
            "https://central.sonatype.com/repository/maven-snapshots/"
          if (isSnapshot.value) Some("central-snapshots" at centralSnapshots)
          else staging
      }
    }
  )

  def getVersion(state: State): String =
    (ThisBuild / version).get(Project.extract(state).structure.data) match {
      case Some(v) => v
      case None    => throw new NoSuchFieldError("version")
    }

  def getPublishCommand(state: State): String =
    (ThisBuild / cireleasePublishCommand).get(
      Project.extract(state).structure.data
    ) match {
      case Some(v) => v
      case None    => throw new NoSuchFieldError("cireleasePublishCommand")
    }

  def isSnapshotVersion(v: String): Boolean = v.endsWith("-SNAPSHOT")

  def backPubVersionToCommand(ver: String): String =
    if (ver.contains("@")) {
      val nonComment =
        if (ver.contains("#")) ver.split("#").head
        else ver
      val commands0 = nonComment.split("@").toList.drop(1)
      var nonDigit = false
      val commands = (commands0.map { cmd =>
        if (cmd.isEmpty) sys.error(s"Invalid back-publish version: $ver")
        else {
          if (!cmd.head.isDigit) {
            nonDigit = true
            cmd
          } else if (cmd.contains(".x")) s"++${cmd}"
          else s"++${cmd}!"
        }
      }) ::: (if (nonDigit) Nil else List("publishSigned"))
      commands match {
        case x :: Nil => x
        case xs       => xs.mkString(";", ";", "")
      }
    } else "+publishSigned"

  def dropBackPubCommand(ver: String): String = {
    val nonComment =
      if (ver.contains("#")) ver.split("#").head
      else ver
    if (nonComment.contains("@")) nonComment.split("@").head
    else nonComment
  }
}
