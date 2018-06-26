package persistence

import com.mongodb.ConnectionString
import com.typesafe.config.ConfigFactory
import org.mongodb.scala.connection.ClusterSettings
import org.mongodb.scala.{MongoClient, MongoClientSettings, MongoCollection, MongoCredential}
import tokens.Token
import users.User

/**
  * Created by spectrum on 5/14/2018.
  */
object PrismMongoClient {

  import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
  import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY
  import org.mongodb.scala.bson.codecs.Macros._

  val conf = ConfigFactory.load()

  val db = conf.getString("mongodb.db")
  val user = conf.getString("mongodb.user")
  val password = conf.getString("mongodb.password")
  val host = conf.getString("mongodb.host")
  val port = conf.getString("mongodb.port")

  val clientSettingsBuilder = MongoClientSettings.builder()
  val mongoClusterSettingsBuilder = ClusterSettings.builder()
  val mongoConnectionString = new ConnectionString(s"mongodb://$host:$port")
  mongoClusterSettingsBuilder.applyConnectionString(mongoConnectionString)

  val credential = MongoCredential.createScramSha1Credential(user, db, password.toCharArray)
  clientSettingsBuilder.credential(credential)
  clientSettingsBuilder.clusterSettings(mongoClusterSettingsBuilder.build())

  val codecRegistry = fromRegistries(
    fromProviders(
      classOf[Token],
      classOf[User]),
    DEFAULT_CODEC_REGISTRY)

  val mongoClient = MongoClient(clientSettingsBuilder.build())

  val prismDB = mongoClient.getDatabase(db).withCodecRegistry(codecRegistry)

  def getTokenCollection: MongoCollection[Token] = prismDB.getCollection("tokens")

  def getUsersCollection: MongoCollection[User] = prismDB.getCollection("users")
}
