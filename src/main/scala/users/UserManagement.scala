package users

import com.mongodb.client.model.UpdateOptions
import helpers.Helpers._
import org.bson.types.ObjectId
import org.mongodb.scala.model.Filters._
import persistence.PrismMongoClient

final class UserNotFound extends Throwable

object User {
  def apply(userSpec: UserSpec): User = new User(new ObjectId().toString, userSpec.name, userSpec.surname,
    userSpec.username, userSpec.email, userSpec.passwordHash)
}

case class User(_id: String,
                name: String,
                surname: String,
                username: String,
                email: String,
                passwordHash: String)

case class UserSpec(name: String,
                    surname: String,
                    username: String,
                    email: String,
                    passwordHash: String) {
  def isValid = {
    name != null && name.nonEmpty &&
      surname != null && surname.nonEmpty &&
      username != null && username.nonEmpty &&
      email != null &&
      """(\w+)@([\w\.]+)""".r.unapplySeq(email.toLowerCase).isDefined
    passwordHash != null && passwordHash.nonEmpty
  }
}

object UserManagement {
  val usersCollection = PrismMongoClient.getUsersCollection

  def createUser(userSpec: UserSpec) = {
    val newUser = User(userSpec)

    usersCollection.insertOne(newUser).results()
    val insertedUser = getByUsername(userSpec.username)
  }

  def getByUsername(username: String): Option[User] = {
    val users = usersCollection.find(equal("username", username)).first().results()

    if (users.nonEmpty) Some(users(0))
    else None
  }

  def deleteUser(username: String) = usersCollection.deleteOne(equal("username", username)).results()

  def getAllUsers = usersCollection.find().results

  def save(user: User) = usersCollection.replaceOne(equal("username", user.username), user, new UpdateOptions().upsert(true)).results()
}
