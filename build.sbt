inThisBuild(
  List(
    organization := "com.github.sbt",
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

publish / skip := true // don't publish the root project

lazy val plugin = project
  .enablePlugins(SbtPlugin)
  .settings(
    moduleName := "sbt-ci-release",
    pluginCrossBuild / sbtVersion := "1.0.4",
    addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.0.1"),
    addSbtPlugin("com.github.sbt" % "sbt-git" % "2.0.1"),
    addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.2.1"),
    addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.11.2")
  )
