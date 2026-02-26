lazy val scala212 = "2.12.21"
lazy val scala3 = "3.8.1"

inThisBuild(
  List(
    organization := "com.github.sbt",
    homepage := Some(url("https://github.com/sbt/sbt-ci-release")),
    licenses := Seq(
      "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
    ),
    developers := List(
      Developer(
        "olafurpg",
        "Ólafur Páll Geirsson",
        "olafurpg@gmail.com",
        url("https://geirsson.com")
      )
    )
  )
)

onLoadMessage := s"Welcome to sbt-ci-release ${version.value}"
crossScalaVersions := Nil
publish / skip := true // don't publish the root project

lazy val plugin = project
  .enablePlugins(SbtPlugin)
  .settings(
    moduleName := "sbt-ci-release",
    crossScalaVersions := Seq(scala212, scala3),
    scalacOptions ++= {
      scalaBinaryVersion.value match {
        case "2.12" => "-Xsource:3" :: "-release:8" :: Nil
        case _      => Nil
      }
    },
    (pluginCrossBuild / sbtVersion) := {
      scalaBinaryVersion.value match {
        case "2.12" => "1.5.8"
        case _      => "2.0.0-RC9"
      }
    },
    libraryDependencies += "org.scalameta" %% "munit" % "1.2.3" % Test,
    addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.1.1"),
    addSbtPlugin("com.github.sbt" % "sbt-git" % "2.1.0"),
    addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.3.1")
  )
