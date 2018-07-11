package users

import com.mongodb.client.model.UpdateOptions
import com.typesafe.scalalogging.Logger
import helpers.Helpers._
import org.bson.types.ObjectId
import org.mongodb.scala.bson.conversions
import org.mongodb.scala.model.Filters._
import persistence.PrismMongoClient
import tokens.LoginSpec

import scala.collection.mutable.ListBuffer

final class UserNotFound extends Throwable

object User {
  def apply(userSpec: UserSpec): User = new User(new ObjectId().toString, userSpec.name, userSpec.surname,
    userSpec.handle, userSpec.email, userSpec.passwordHash)
}

case class User(_id: String,
                name: String,
                surname: String,
                handle: String,
                email: String,
                passwordHash: String)

case class UserSpec(name: String,
                    surname: String,
                    handle: String,
                    email: String,
                    passwordHash: String) {
  def isValid = {
      name != null && name.nonEmpty &&
      surname != null && surname.nonEmpty &&
      handle != null && handle.nonEmpty &&
      email != null &&
      """(\w+)@([\w\.]+)""".r.unapplySeq(email.toLowerCase).isDefined
      passwordHash != null && passwordHash.nonEmpty
  }
}

case class UserSearchCriteria(userIds: Option[List[String]] = None)

class UserManagement
object UserManagement {

  val usersCollection = PrismMongoClient.getUsersCollection
  val logger = Logger[UserManagement]

  def exists(loginSpec: LoginSpec) = {
    val matches = usersCollection.find(and(equal("handle", loginSpec.handle), equal("passwordHash", loginSpec.passwordHash))).results()

    if(matches.length > 1){
      logger.error(s"Inconsistent state: ${matches.length} users with same credentials found")
      false
    } else if(matches.isEmpty)
      false
    else
      true
  }

  def getUsers(userSearchCriteria: UserSearchCriteria) = {
    val filters = new ListBuffer[conversions.Bson]()

    userSearchCriteria.userIds match {
      case Some(userIds) => filters += in("handle", userIds: _*)
      case None => ;
    }

    if (!filters.isEmpty)
      usersCollection.find(and(filters: _*)).results()
    else
      usersCollection.find().results()
  }

  def createUser(userSpec: UserSpec) = {
    val newUser = User(userSpec)

    usersCollection.insertOne(newUser).results()
    getByUsername(userSpec.handle)
  }

  def getByUsername(username: String): Option[User] = {
    val users = usersCollection.find(equal("handle", username)).first().results()

    if (users.nonEmpty)
      Some(users(0))
    else
      None
  }

  def deleteUser(username: String) = usersCollection.deleteOne(equal("handle", username)).results()

  def getAllUsers = usersCollection.find().results

  def save(user: User) = usersCollection.replaceOne(equal("handle", user.handle), user, new UpdateOptions().upsert(true)).results()
}
