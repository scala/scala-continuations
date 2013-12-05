import Settings._
import Keys.{`package` => packageTask }

// plugin logic of build based on https://github.com/retronym/boxer

lazy val commonSettings = Seq(
  repoName                   := "scala-continuations",
  organization               := "org.scala-lang.plugins",
  version                    := "1.0.0-SNAPSHOT",
  scalaVersion               := "2.11.0-M7",
  snapshotScalaBinaryVersion := "2.11.0-M7"
)

lazy val root = project.in( file(".") ).settings( publishArtifact := false ).aggregate(plugin, library).settings(commonSettings : _*)

lazy val plugin = project settings (
  name                := "scala-continuations-plugin",
  libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value
) settings(scalacPluginSettings: _*) settings (commonSettings : _*)

val pluginJar = packageTask in (plugin, Compile)

// TODO: the library project's test are really plugin tests, but we first need that jar
lazy val library = project.settings(scalaModuleSettings: _*).settings(commonSettings: _*).settings(
  name                 := "scala-continuations-library",
  scalacOptions       ++= Seq(
    // add the plugin to the compiler
    s"-Xplugin:${pluginJar.value.getAbsolutePath}",
    // enable the plugin
    "-P:continuations:enable",
    // add plugin timestamp to compiler options to trigger recompile of
    // the library after editing the plugin. (Otherwise a 'clean' is needed.)
    s"-Jdummy=${pluginJar.value.lastModified}"),
  libraryDependencies ++= Seq(
    "org.scala-lang" % "scala-compiler" % scalaVersion.value % "test",
    "junit" % "junit" % "4.11" % "test",
    "com.novocode" % "junit-interface" % "0.10" % "test"),
  testOptions          += Tests.Argument(
    TestFrameworks.JUnit,
    s"-Dscala-continuations-plugin.jar=${pluginJar.value.getAbsolutePath}"
  ),
  OsgiKeys.exportPackage := Seq(s"scala.util.continuations;version=${version.value}")
)
