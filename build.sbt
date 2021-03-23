inThisBuild(
  List(
    organization := "com.geirsson",
    homepage := Some(url("https://github.com/olafurpg/sbt-ci-release")),
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

skip in publish := true // don't publish the root project

lazy val plugin = project
  .enablePlugins(SbtPlugin)
  .settings(
    moduleName := "sbt-ci-release",
    sbtVersion in pluginCrossBuild := "1.0.4",
    addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1"),
    addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "1.0.0"),
    addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.7"),
    addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.1.2")
  )
