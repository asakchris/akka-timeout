name := "event-processor"

version := "0.1.3.1-SNAPSHOT"

scalaVersion := "2.12.6"

//sbt native packager.  See README for details
enablePlugins(JavaAppPackaging)
enablePlugins(UniversalPlugin)

lazy val specsVersion = "4.2.0"
lazy val akkaVersion = "2.5.21"
lazy val slickVersion = "3.2.3"
lazy val alpakkaVersion = "1.0-M1"
lazy val slf4jVersion = "1.7.25"
lazy val specs2Version = "4.2.0"
lazy val akkaManagementVersion = "1.0.0-RC2"

resolvers += Resolver.mavenLocal

libraryDependencies ++= Seq(
  // akka
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  //streams
  "com.lightbend.akka" %% "akka-stream-alpakka-slick" % alpakkaVersion,
  // cluster
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-metrics" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
  // management
  "com.lightbend.akka.management" %% "akka-management" % akkaManagementVersion,
  "com.lightbend.akka.management" %% "akka-management-cluster-http" % akkaManagementVersion,

  //slick
  //"com.typesafe.slick" %% "slick" % slickVersion,
  //"com.typesafe.slick" %% "slick-hikaricp" % slickVersion,

  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.9.8",

  //test
  "org.scalatest" %% "scalatest" % "3.0.5" % Test, //scala's jUnit equivalent
  "org.scalacheck" %% "scalacheck" % "1.14.0" % Test, //property testing

  "org.specs2" %% "specs2-junit" % specs2Version % Test,
  "org.specs2" %% "specs2-core" % specs2Version % Test,

  "junit" % "junit" % "4.12" % Test,
  "org.junit.jupiter" % "junit-jupiter-api" % "5.3.2" % Test,

  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion % Test,
  "ch.qos.logback" % "logback-classic" % "1.2.3" % Test,
  "org.slf4j" % "log4j-over-slf4j" % slf4jVersion % Test,
  // Schema registry uses Glassfish which uses java.util.logging
  "org.slf4j" % "jul-to-slf4j" % slf4jVersion % Test,

  //slick
  "com.typesafe.slick" %% "slick-testkit" % slickVersion % Test, //slick testkit
  "com.h2database" % "h2" % "1.4.196" % Test, //h2 test utils
  "com.typesafe.akka" %% "akka-stream-kafka-testkit" % alpakkaVersion % Test
)