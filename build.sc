import mill._
import scalalib._

object hinshofer extends ScalaModule {
  def scalaVersion = "2.13.1"
  def ivyDeps = Agg(
    ivy"org.mongodb.scala::mongo-scala-driver:4.0.4",
    ivy"com.typesafe.akka::akka-actor:2.6.6",
    ivy"com.typesafe.akka::akka-http:10.1.12",
    ivy"com.google.code.gson:gson:2.8.6",
    ivy"com.typesafe.scala-logging::scala-logging:3.9.2",
    ivy"com.typesafe:config:1.4.0",
    ivy"ch.qos.logback:logback-classic:1.2.3",
    ivy"org.quartz-scheduler:quartz:2.3.2",
    ivy"com.typesafe.akka::akka-stream:2.6.6",
    ivy"com.pauldijou::jwt-core:4.3.0"
  )

  def mainClass = Some("Hinshofer")
}
