import Keys.{ `package` => packageTask }

lazy val commonSettings = ScalaModulePlugin.scalaModuleSettings ++
  ScalaModulePlugin.scalaModuleOsgiSettings ++
  Seq(
    scalaModuleRepoName := "scala-continuations",
    organization := "org.scala-lang.plugins",
    scalacOptions ++= Seq("-deprecation", "-feature")
  ) ++ crossVersionSharedSources

lazy val crossVersionSharedSources: Seq[Setting[_]] =
  Seq(Compile, Test).map { sc =>
    (unmanagedSourceDirectories in sc) ++= {
      (unmanagedSourceDirectories in sc ).value.map { dir: File =>
        CrossVersion.partialVersion(scalaVersion.value) match {
          case Some((2, y)) if y == 11 => new File(dir.getPath + "-2.11")
          case Some((2, y)) if y >= 12 => new File(dir.getPath + "-2.12")
        }
      }
    }
  }

lazy val continuations = project.in(file("."))
  .settings(commonSettings: _*)
  .settings(skip in publish := true)
  .aggregate(plugin, library)

lazy val plugin = project
  .settings(commonSettings: _*)
  .settings(
    name                   := "scala-continuations-plugin",
    crossVersion           := CrossVersion.full, // because compiler api is not binary compatible
    libraryDependencies    += "org.scala-lang" % "scala-compiler" % scalaVersion.value,
    OsgiKeys.exportPackage := Seq(s"scala.tools.selectivecps;version=${version.value}")
  )

val pluginJar = packageTask in (plugin, Compile)

// TODO: the library project's test are really plugin tests, but we first need that jar
lazy val library = project
  .settings(commonSettings: _*)
  .settings(
    name                := "scala-continuations-library",
    scalaModuleMimaPreviousVersion := Some("1.0.3"),

    scalacOptions ++= Seq(
      // add the plugin to the compiler
      s"-Xplugin:${pluginJar.value.getAbsolutePath}",
      // enable the plugin
      "-P:continuations:enable",
      // add plugin timestamp to compiler options to trigger recompile of
      // the library after editing the plugin. (Otherwise a 'clean' is needed.)
      s"-Jdummy=${pluginJar.value.lastModified}"),

    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-compiler"  % scalaVersion.value % "test",
      "junit"          % "junit"           % "4.13" % "test",
      "com.novocode"   % "junit-interface" % "0.11" % "test"),

    testOptions += Tests.Argument(
      TestFrameworks.JUnit,
      s"-Dscala-continuations-plugin.jar=${pluginJar.value.getAbsolutePath}"
    ),

    OsgiKeys.exportPackage := Seq(s"scala.util.continuations;version=${version.value}")
  )
