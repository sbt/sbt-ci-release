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
scalaVersion := "2.12.10"
lazy val V = new {
  val scala210 = "2.10.7"
  val scala211 = "2.11.12"
  val scala212 = "2.12.10"
  val scala213 = "2.13.1"
  val scalameta = "4.3.0"
  val semanticdb = scalameta
  val bsp = "2.0.0-M4+10-61e61e87"
  val bloop = "1.3.4+298-2c6ff971"
  val sbtBloop = "1.3.5"
  val gradleBloop = bloop
  val mavenBloop = bloop
  val mdoc = "2.0.3"
  val scalafmt = "2.3.2"
  // List of supported Scala versions in SemanticDB. Needs to be manually updated
  // for every SemanticDB upgrade.
  def supportedScalaVersions =
    nonDeprecatedScalaVersions ++ deprecatedScalaVersions
  def deprecatedScalaVersions = Seq("2.12.8", "2.12.9", scala211)
  def nonDeprecatedScalaVersions = Seq("2.13.0", scala213, scala212)
  def guava = "com.google.guava" % "guava" % "28.1-jre"
  def lsp4j = "org.eclipse.lsp4j" % "org.eclipse.lsp4j" % "0.8.1"
  def dap4j =
    "org.eclipse.lsp4j" % "org.eclipse.lsp4j.debug" % "0.8.1"
  val coursier = "2.0.0-RC5-3"
}

libraryDependencies ++= List(
  "org.apache.spark" %% "spark-sql" % "3.0.0-preview",
      "com.thoughtworks.qdox" % "qdox" % "2.0.0", // for java mtags
      "org.jsoup" % "jsoup" % "1.12.1", // for extracting HTML from javadocs
      "org.lz4" % "lz4-java" % "1.7.0", // for streaming hashing when indexing classpaths
      "com.lihaoyi" %% "geny" % "0.1.8",
      "org.scala-lang.modules" %% "scala-java8-compat" % "0.9.0",
      "org.scalameta" % "semanticdb-scalac-core" % V.scalameta cross CrossVersion.full,
      V.lsp4j,
      "com.chuusai" %% "shapeless" % "2.3.3",
      "org.typelevel" %% "cats-core" % "2.1.0",
      "org.typelevel" %% "simulacrum" % "1.0.0",
      "com.olegpy" %% "better-monadic-for" % "0.3.1",
      "org.typelevel" %% "kind-projector" % "0.10.3",
      // =================
      // Java dependencies
      // =================
      // for bloom filters
      V.guava,
      // for measuring memory footprint
      "org.openjdk.jol" % "jol-core" % "0.9",
      // for file watching
      "io.methvin" % "directory-watcher" % "0.8.0",
      // for http client
      "io.undertow" % "undertow-core" % "2.0.28.Final",
      "org.jboss.xnio" % "xnio-nio" % "3.7.7.Final",
      // for persistent data like "dismissed notification"
      "org.flywaydb" % "flyway-core" % "6.1.2",
      "com.h2database" % "h2" % "1.4.200",
      // for starting `sbt bloopInstall` process
      "com.zaxxer" % "nuprocess" % "1.2.4",
      "net.java.dev.jna" % "jna" % "4.5.2",
      "net.java.dev.jna" % "jna-platform" % "4.5.2",
      // for token edit-distance used by goto definition
      "com.googlecode.java-diff-utils" % "diffutils" % "1.3.0",
      // for BSP
      "org.scala-sbt.ipcsocket" % "ipcsocket" % "1.0.0",
      "ch.epfl.scala" % "bsp4j" % V.bsp,
      "ch.epfl.scala" %% "bloop-launcher" % V.bloop,
      // for LSP
      V.lsp4j,
      // for DAP
      V.dap4j,
      // for producing SemanticDB from Java source files
      "com.thoughtworks.qdox" % "qdox" % "2.0.0",
      // for finding paths of global log/cache directories
      "io.github.soc" % "directories" % "11",
      // ==================
      // Scala dependencies
      // ==================
      "org.scala-lang.modules" %% "scala-java8-compat" % "0.9.0",
      "org.scalameta" % "mdoc-interfaces" % V.mdoc,
      "org.scalameta" %% "scalafmt-dynamic" % V.scalafmt,
      // For reading classpaths.
      // for fetching ch.epfl.scala:bloop-frontend and other library dependencies
      "io.get-coursier" % "interface" % "0.0.16",
      // for logging
      "com.outr" %% "scribe" % "2.7.10",
      "com.outr" %% "scribe-slf4j" % "2.7.10", // needed for flyway database migrations
      // for debugging purposes, not strictly needed but nice for productivity
      "com.lihaoyi" %% "pprint" % "0.5.6",
      // For exporting Pants builds.
      "com.lihaoyi" %% "ujson" % "0.9.0",
      "ch.epfl.scala" %% "bloop-config" % V.bloop,
      // for producing SemanticDB from Scala source files
      "org.scalameta" %% "scalameta" % V.scalameta,
      "org.scalameta" % "semanticdb-scalac-core" % V.scalameta cross CrossVersion.full
  )
//
// onLoadMessage := s"Welcome to sbt-ci-release ${version.value}"
//
// skip in publish := true // don't publish the root project
//
// lazy val plugin = project
//   .settings(
//     moduleName := "sbt-ci-release",
//     sbtPlugin := true,
//     sbtVersion in pluginCrossBuild := "1.0.4",
//     addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.0.0"),
//     addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "1.0.0"),
//     addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.8.1"),
//     addSbtPlugin("com.jsuereth" % "sbt-pgp" % "2.0.0")
//   )
