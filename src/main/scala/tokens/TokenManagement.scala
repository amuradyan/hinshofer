package tokens

import com.typesafe.config.ConfigFactory
import helpers.Helpers._
import org.bson.types.ObjectId
import org.mongodb.scala.model.Filters._
import org.quartz.impl.StdSchedulerFactory
import org.quartz.{Job, JobExecutionContext}
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim, JwtHeader}
import persistence.PrismMongoClient
import users.UserModel

/**
  * Created by spectrum on 5/14/2018.
  */

case class BareToken(token: String)

case class Token(_id: String, token: String)

case class LoginSpec(handle: String, passwordHash: String) {
  def isValid = handle != null && handle.trim.nonEmpty && passwordHash != null && passwordHash.trim.nonEmpty
}

class TokenCleanup extends Job {
  val tokenCollection = PrismMongoClient.getTokenCollection

  override def execute(context: JobExecutionContext) = {
    tokenCollection.drop().results()
  }
}

class TokenManagement

object TokenManagement {

  val conf = ConfigFactory.load()
  val secretKey = conf.getString("app.secret_key")
  val tokenLife = conf.getInt("app.token_life")
  val scheduler = StdSchedulerFactory.getDefaultScheduler()
  val tokenCollection = PrismMongoClient.getTokenCollection

  def setup {
    import org.quartz.JobBuilder._
    import org.quartz.SimpleScheduleBuilder._
    import org.quartz.TriggerBuilder._

    val job = newJob(classOf[TokenCleanup]).withIdentity("token-cleanup", "cleanup").build()

    val trigger = newTrigger.withIdentity("token-cleanup-trigger", "cleanup-triggers")
      .startNow()
      .withSchedule(simpleSchedule()
        .withIntervalInHours(12)
        .repeatForever())
      .build()

    scheduler.scheduleJob(job, trigger)
  }

  def issueToken(user: UserModel) = {

    val header = JwtHeader(JwtAlgorithm.HS512, "JWT")

    val claim = JwtClaim()
      .issuedAt(System.currentTimeMillis())
      .expiresAt(System.currentTimeMillis() + tokenLife)
      .about(user._id)
    Jwt.encode(header, claim, secretKey)
  }

  def isTokenBlacklisted(token: String) = {
    val tokens = tokenCollection.find(equal("token", token)).first().results()

    !tokens.isEmpty
  }

  def blacklistToken(token: String) = {
    if (!isTokenBlacklisted(token) && isValid(token))
      tokenCollection.insertOne(Token(new ObjectId().toString, token)).results()
  }

  def isValid(token: String) = Jwt.isValid(token, secretKey, Seq(JwtAlgorithm.HS512))

  def decode(token: String): JwtClaim = Jwt.decode(token, secretKey, Seq(JwtAlgorithm.HS512)).get
}
