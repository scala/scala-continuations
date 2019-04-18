import Keys.{ `package` => packageTask }
import ScalaModulePlugin._

// plugin logic of build based on https://github.com/retronym/boxer

scalaVersionsByJvm in ThisBuild := {
  val vs = List("2.11.12", "2.12.8")
  // Map[JvmVersion, List[(ScalaVersion, UseForPublishing)]]
  Map(
    8 -> vs.map(_ -> true),
    11 -> vs.map(_ -> false),
    12 -> vs.map(_ -> false)
  )
}

lazy val commonSettings = scalaModuleSettings ++ Seq(
  repoName     := "scala-continuations",
  organization := "org.scala-lang.plugins",
  version      := "1.0.3-SNAPSHOT",

  licenses := Seq(("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0"))),

  scalacOptions ++= Seq(
    "-deprecation",
    "-feature")
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

lazy val root = project.in(file("."))
  .settings(commonSettings: _*)
  .settings(publishArtifact := false)
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
    mimaPreviousVersion := Some("1.0.3"),

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
      "junit"          % "junit"           % "4.12" % "test",
      "com.novocode"   % "junit-interface" % "0.11" % "test"),

    testOptions += Tests.Argument(
      TestFrameworks.JUnit,
      s"-Dscala-continuations-plugin.jar=${pluginJar.value.getAbsolutePath}"
    ),

    OsgiKeys.exportPackage := Seq(s"scala.util.continuations;version=${version.value}")
  )
