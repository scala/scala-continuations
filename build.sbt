import com.typesafe.tools.mima.plugin.{MimaPlugin, MimaKeys}
import Keys.{`package` => packageTask }
import com.typesafe.sbt.osgi.{OsgiKeys, SbtOsgi}

// plugin logic of build based on https://github.com/retronym/boxer

lazy val commonSettings = scalaModuleSettings ++ Seq(
  repoName                   := "scala-continuations",
  organization               := "org.scala-lang.plugins",
  version                    := "1.0.3-SNAPSHOT",
  scalaVersion               := "2.11.8",
  crossScalaVersions         := Seq("2.11.8", "2.12.0-M5"),
  snapshotScalaBinaryVersion := "2.11.8",
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature")
)

lazy val root = project.in( file(".") ).settings( publishArtifact := false ).aggregate(plugin, library).settings(commonSettings : _*)

lazy val plugin = project settings (scalaModuleOsgiSettings: _*) settings (
  name                   := "scala-continuations-plugin",
  crossVersion           := CrossVersion.full, // because compiler api is not binary compatible
  libraryDependencies    += "org.scala-lang" % "scala-compiler" % scalaVersion.value,
  OsgiKeys.exportPackage := Seq(s"scala.tools.selectivecps;version=${version.value}")
) settings (commonSettings : _*)

val pluginJar = packageTask in (plugin, Compile)

// TODO: the library project's test are really plugin tests, but we first need that jar
lazy val library = project settings (scalaModuleOsgiSettings: _*) settings (MimaPlugin.mimaDefaultSettings: _*) settings (
  name                       := "scala-continuations-library",
  MimaKeys.mimaPreviousArtifacts  := Set(organization.value % s"${name.value}_2.11.0-RC1" % "1.0.0"),
  scalacOptions       ++= Seq(
    // add the plugin to the compiler
    s"-Xplugin:${pluginJar.value.getAbsolutePath}",
    // enable the plugin
    "-P:continuations:enable",
    // add plugin timestamp to compiler options to trigger recompile of
    // the library after editing the plugin. (Otherwise a 'clean' is needed.)
    s"-Jdummy=${pluginJar.value.lastModified}"),
  libraryDependencies ++= Seq(
    "org.scala-lang" % "scala-compiler"  % scalaVersion.value % "test",
    "junit"          % "junit"           % "4.11" % "test",
    "com.novocode"   % "junit-interface" % "0.10" % "test"),
  testOptions          += Tests.Argument(
    TestFrameworks.JUnit,
    s"-Dscala-continuations-plugin.jar=${pluginJar.value.getAbsolutePath}"
  ),
  // run mima during tests
  test in Test := {
    MimaKeys.mimaReportBinaryIssues.value
    (test in Test).value
  },
  OsgiKeys.exportPackage := Seq(s"scala.util.continuations;version=${version.value}")
) settings (commonSettings : _*)
