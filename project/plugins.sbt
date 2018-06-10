addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "7533cfd7-SNAPSHOT")
resolvers += Resolver.sonatypeRepo("snapshots")
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "2.1.0")
addSbtPlugin(
  "io.get-coursier" % "sbt-coursier" % coursier.util.Properties.version
)
addSbtPlugin(
  "io.get-coursier" % "sbt-shading" % coursier.util.Properties.version
)
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.3")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.0")
libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
