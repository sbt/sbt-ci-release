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
  .enablePlugins(GraalVMNativeImagePlugin)
  .settings(
    moduleName := "sbt-ci-release",
    sbtPlugin := true,
    sbtVersion in pluginCrossBuild := "1.0.4",
    addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.0.0"),
    addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "1.0.0"),
    addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.7"),
    addSbtPlugin("com.jsuereth" % "sbt-pgp" % "2.0.0"),
    mainClass in GraalVMNativeImage := Some("com.geirsson.Main"),
    graalVMNativeImageOptions ++= {
      val reflectionFile =
        sourceDirectory.in(Compile).value./("graal")./("reflection.json")
      assert(reflectionFile.exists, s"no such file: $reflectionFile")
      List(
        "--no-server",
        "--enable-http",
        "--enable-https",
        "-H:EnableURLProtocols=http,https",
        "--enable-all-security-services",
        "--no-fallback",
        s"-H:ReflectionConfigurationFiles=$reflectionFile",
        //"--allow-incomplete-classpath",
        "-H:+ReportExceptionStackTraces"
        //"--initialize-at-build-time=scala.Function1"
      )
    }
  )
