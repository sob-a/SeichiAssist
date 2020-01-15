import java.io._

import scala.io.Source

ThisBuild / scalaVersion     := "2.13.1"
ThisBuild / version          := "1.2.4"
ThisBuild / organization     := "click.seichi"
ThisBuild / description      := "ギガンティック☆整地鯖の独自要素を司るプラグイン"

resolvers ++= Seq(
  "jitpack.io"             at "https://jitpack.io",
  "maven.sk89q.com"        at "https://maven.sk89q.com/repo/",
  "maven.playpro.com"      at "https://maven.playpro.com",
  "repo.spring.io"         at "https://repo.spring.io/plugins-release/",
  "repo.spongepowered.org" at "https://repo.spongepowered.org/maven",
  "repo.maven.apache.org"  at "https://repo.maven.apache.org/maven2",
  "hub.spigotmc.org"       at "https://hub.spigotmc.org/nexus/content/repositories/snapshots",
  "oss.sonatype.org"       at "https://oss.sonatype.org/content/repositories/snapshots",
  "nexus.okkero.com"       at "https://nexus.okkero.com/repository/maven-releases/"
)

val providedDependencies = Seq(
  "org.jetbrains" % "annotations" % "17.0.0",
  "org.apache.commons" % "commons-lang3" % "3.9",
  "commons-codec" % "commons-codec" % "1.12",
  "org.spigotmc" % "spigot-api" % "1.12.2-R0.1-SNAPSHOT",
  "com.sk89q.worldguard" % "worldguard-legacy" % "6.2",
  "net.coreprotect" % "coreprotect" % "2.14.2"
).map(_ % "provided")

val testDependencies = Seq(
  "org.junit.jupiter" % "junit-jupiter-api" % "5.4.2",
  "junit" % "junit" % "4.4",
  "org.junit.jupiter" % "junit-jupiter-engine" % "5.4.2"
).map(_ % "test")

val dependenciesToEmbed = Seq(
  "org.flywaydb" % "flyway-core" % "5.2.4",
  "org.typelevel" %% "cats-core" % "2.0.0",
  "org.typelevel" %% "cats-effect" % "2.0.0",
  "eu.timepit" %% "refined" % "0.9.10",
  "com.beachape" %% "enumeratum" % "1.5.13"
)

// localDependenciesはprovidedとして扱い、jarに埋め込まない
assemblyExcludedJars in assembly := {
  (fullClasspath in assembly).value
    .filter { a =>
      def directoryContainsFile(directory: File, file: File) =
        file.absolutePath.startsWith(directory.absolutePath)

      directoryContainsFile(baseDirectory.value / "localDependencies", a.data)
    }
}

val tokenReplacementMap = settingKey[Map[String, String]]("Map specifying what tokens should be replaced to")

tokenReplacementMap := Map(
  "name" -> name.value,
  "version" -> version.value
)

def replaceTokens(out: File, file: File, map: Map[String, String]): File = {
  out.getParentFile.mkdirs()

  val input = new FileInputStream(file)
  val output = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out))))

  Source.fromInputStream(input).getLines().foreach { line =>
    val replaced =
      map.foldLeft(line) { case (acc, (key, value)) =>
        acc.replaceAll(s"@$key@", value)
      }

    output.println(replaced)
  }

  output.flush()
  output.close()

  out
}

val filesToBeReplaced = Seq("plugin.yml")

val filteredResourceGenerator = taskKey[Seq[File]]("Resource generator to filter resources")

filteredResourceGenerator in Compile :=
  filesToBeReplaced
    .map { relativeToResourceDir =>
      val outFile = (resourceManaged in Compile).value / relativeToResourceDir
      val file = (resourceDirectory in Compile).value / relativeToResourceDir
      replaceTokens(outFile, file, tokenReplacementMap.value)
    }

resourceGenerators in Compile += (filteredResourceGenerator in Compile)

unmanagedResources in Compile += baseDirectory.value / "LICENSE"

// トークン置換を行ったファイルをunmanagedResourcesのコピーから除外する
excludeFilter in unmanagedResources :=
  filesToBeReplaced.foldLeft((excludeFilter in unmanagedResources).value)(_.||(_))

lazy val root = (project in file("."))
  .settings(
    name := "SeichiAssist",
    assemblyJarName in assembly := s"SeichiAssist-${version.value}.jar",
    libraryDependencies := providedDependencies ++ testDependencies ++ dependenciesToEmbed,
    unmanagedBase := baseDirectory.value / "localDependencies",
    scalacOptions ++= Seq(
      "-encoding", "utf8",
      "-unchecked",
      "-deprecation",
      "-Ypatmat-exhaust-depth", "80",
    ),
    javacOptions ++= Seq("-encoding", "utf8")
  )
