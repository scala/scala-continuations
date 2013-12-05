import sbt._
import Keys._
import com.typesafe.sbt.osgi.{OsgiKeys, SbtOsgi}

object Settings {
  val snapshotScalaBinaryVersion = settingKey[String]("The Scala binary version to use when building against Scala SNAPSHOT.")
  val repoName = settingKey[String]("The name of the repository under github.com/scala/.")

  def deriveBinaryVersion(sv: String, snapshotScalaBinaryVersion: String) = sv match {
    case snap_211 if snap_211.startsWith("2.11") &&
                     snap_211.contains("-SNAPSHOT") => snapshotScalaBinaryVersion
    case sv => sbt.CrossVersion.binaryScalaVersion(sv)
  }

  val osgiVersion = version(_.replace('-', '.'))

  lazy val scalaModuleSettings = Seq(
    repoName := name.value,

    scalaBinaryVersion := deriveBinaryVersion(scalaVersion.value, snapshotScalaBinaryVersion.value),

    // to allow compiling against snapshot versions of Scala
    resolvers += Resolver.sonatypeRepo("snapshots"),

    // don't use for doc scope, scaladoc warnings are not to be reckoned with
    // TODO: turn on for nightlies, but don't enable for PR validation... "-Xfatal-warnings"
    scalacOptions in compile ++= Seq("-optimize", "-feature", "-deprecation", "-unchecked", "-Xlint"),

    // Generate $name.properties to store our version as well as the scala version used to build
    resourceGenerators in Compile <+= Def.task {
      val props = new java.util.Properties
      props.put("version.number", version.value)
      props.put("scala.version.number", scalaVersion.value)
      props.put("scala.binary.version.number", scalaBinaryVersion.value)
      val file = (resourceManaged in Compile).value / s"${name.value}.properties"
      IO.write(props, null, file)
      Seq(file)
    },


    // maven publishing
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (version.value.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },

    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),

    publishMavenStyle := true,

    publishArtifact in Test := false,

    pomIncludeRepository := { _ => false },

    pomExtra := (
      <url>http://www.scala-lang.org/</url>
      <inceptionYear>2010</inceptionYear>
      <licenses>
        <license>
            <distribution>repo</distribution>
            <name>BSD 3-Clause</name>
            <url>https://github.com/scala/{repoName.value}/blob/master/LICENSE.md</url>
        </license>
       </licenses>
      <scm>
        <connection>scm:git:git://github.com/scala/{repoName.value}.git</connection>
        <url>https://github.com/scala/{repoName.value}</url>
      </scm>
      <issueManagement>
        <system>JIRA</system>
        <url>https://issues.scala-lang.org/</url>
      </issueManagement>
      <developers>
        <developer>
          <id>epfl</id>
          <name>EPFL</name>
        </developer>
        <developer>
          <id>Typesafe</id>
          <name>Typesafe, Inc.</name>
        </developer>
      </developers>
    ),

    OsgiKeys.bundleSymbolicName := s"${organization.value}.${name.value}",
    OsgiKeys.bundleVersion := osgiVersion.value,
    // Sources should also have a nice MANIFEST file
    packageOptions in packageSrc := Seq(
      Package.ManifestAttributes(
        ("Bundle-SymbolicName", s"${organization.value}.${name.value}.source"),
        ("Bundle-Name", s"${name.value} sources"),
        ("Bundle-Version", osgiVersion.value),
        ("Eclipse-SourceBundle", s"""${organization.value}.${name.value};version="${osgiVersion.value}";roots:="."""")
      )
    )

    // TODO: mima
    // import com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings
    // import com.typesafe.tools.mima.plugin.MimaKeys.previousArtifact
    // previousArtifact := Some(organization.value %% name.value % binaryReferenceVersion.value)
  )
}
