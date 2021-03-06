name := "crypto-trading"

version := "0.1"

scalaVersion := "2.12.4"

val workaround = {
  sys.props += "packaging.type" -> "jar"
  ()
}

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
  "io.reactivex" %% "rxscala" % "0.26.5"
)

libraryDependencies ++= Seq(
  "org.knowm.xchange" % "xchange-binance" % "4.3.2",
  "org.knowm.xchange" % "xchange-bittrex" % "4.3.2",
  "org.knowm.xchange" % "xchange-bitfinex" % "4.3.2",
  "org.knowm.xchange" % "xchange-bitstamp" % "4.3.2",
  "org.knowm.xchange" % "xchange-cryptopia" % "4.3.2",
  "org.knowm.xchange" % "xchange-hitbtc" % "4.3.2",
  "org.knowm.xchange" % "xchange-liqui" % "4.3.2",
  "org.knowm.xchange" % "xchange-poloniex" % "4.3.2",
  "org.knowm.xchange" % "xchange-okcoin" % "4.3.2"
)

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.0.4" % Test,
  "org.mockito" % "mockito-all" % "2.0.2-beta" % Test,
  "com.github.tomakehurst" % "wiremock" % "2.14.0" % Test
)

