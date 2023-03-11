unmanagedSourceDirectories.in(Compile) +=
  baseDirectory.in(ThisBuild).value.getParentFile /
    "plugin" / "src" / "main" / "scala"
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.18")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.2.1")
addSbtPlugin("com.github.sbt" % "sbt-git" % "2.0.0")
