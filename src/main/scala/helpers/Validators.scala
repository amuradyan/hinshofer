package helpers

import com.typesafe.config.ConfigFactory

/**
  * Created by spectrum on 6/4/2018.
  */
object Validators {
  lazy val conf = ConfigFactory.load()
  lazy val bots = conf.getStringList("app.bots")
  lazy val regions = conf.getStringList("app.regions")
  lazy val roles = conf.getStringList("app.roles")

  def isValidSource(source: String): Boolean = isValidBotName(source) || source.equalsIgnoreCase("manual")

  def isEmail(email: String) = """(\w+)@([\w\.]+)""".r.unapplySeq(email.toLowerCase).isDefined

  def isPhoneNumber(phoneNumber: String) = """^\+\d{5,17}$""".r.unapplySeq(phoneNumber.toLowerCase).isDefined

  def isRegion(region: String) = regions.contains(region.toLowerCase)

  def isRole(role: String) = roles.contains(role.toLowerCase)

  def isValidBotName(botName: String) = bots.contains(botName.toLowerCase)

  def isValidBookName(bookName: String) =  "profit".equals(bookName) || isValidBotName(bookName)

  def isValidType(`type`: String) = `type`.equalsIgnoreCase("deposit") || `type`.equalsIgnoreCase("withdraw")
}
