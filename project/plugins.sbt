unmanagedSourceDirectories.in(Compile) +=
  baseDirectory.in(ThisBuild).value.getParentFile /
    "plugin" / "src" / "main" / "scala"
addSbtPlugin(
  "io.get-coursier" % "sbt-coursier" % coursier.util.Properties.version
)
addSbtPlugin(
  "io.get-coursier" % "sbt-shading" % coursier.util.Properties.version
)
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.0.0")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.3")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.2")
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "1.0.0")
libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
