name := "crypto-trading"

version := "0.1"

scalaVersion := "2.12.4"

val workaround = {
  sys.props += "packaging.type" -> "jar"
  ()
}

val xchangeStreamVersion = "4.3.1"
val logbackVersion = "1.2.3"
val circeVersion = "0.8.0"
val akkaHttpVersion = "10.0.11"

libraryDependencies += "ch.qos.logback" % "logback-classic" % logbackVersion

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test
)

libraryDependencies ++= Seq(
  "io.circe" %% "circe-parser" % circeVersion,
  "io.circe" %% "circe-generic-extras" % circeVersion,
)

libraryDependencies ++= Seq(
  "org.java-websocket" % "Java-WebSocket" % "1.3.7",
  "io.reactivex" %% "rxscala" % "0.26.5"
)

libraryDependencies ++= Seq(
  "org.knowm.xchange" % "xchange-binance" % "4.3.1"
)