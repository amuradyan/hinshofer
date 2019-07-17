package users

import com.typesafe.scalalogging.Logger
import helpers.Helpers._
import org.bson.types.ObjectId
import org.mongodb.scala.Completed
import org.mongodb.scala.bson.conversions
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates.set
import org.mongodb.scala.result.DeleteResult
import persistence.PrismMongoClient

import scala.collection.mutable.ListBuffer

final class DuplicateEntryFound extends Throwable

abstract sealed trait User {
  def name: String

  def surname: String

  def handle: String

  def email: String

  def passwordHash: String
}

object User {
  def apply(userSpec: UserSpec) =
    new UserModel(new ObjectId().toString, userSpec.name, userSpec.surname, userSpec.handle, userSpec.email, userSpec.passwordHash)

  def apply(userId: String, userSpec: UserSpec) =
    new UserModel(userId, userSpec.name, userSpec.surname, userSpec.handle, userSpec.email, userSpec.passwordHash)
}

case class UserModel(_id: String, name: String, surname: String, handle: String, email: String, passwordHash: String) extends User

sealed case class UserSpec(name: String, surname: String, handle: String, email: String, passwordHash: String) extends User {
  def isValid = {
    handle != null && handle.nonEmpty &&
      email != null &&
      """(\w+)@([\w\.]+)""".r.unapplySeq(email.toLowerCase).isDefined &&
      passwordHash != null && passwordHash.nonEmpty
  }
}

case class UserSearchCriteria(userIds: Option[List[String]] = None)

object UserManagement {

  class UserManagement

  val usersCollection = PrismMongoClient.getUsersCollection
  val logger = Logger[UserManagement]

  @throws[DuplicateEntryFound]
  def getByHandleAndPassword(handle: String, passwordHash: String) = {
    val matches = usersCollection.find(and(equal("handle", handle), equal("passwordHash", passwordHash.toUpperCase))).results()

    if (matches.length > 1) {
      logger.error(s"Inconsistent state: ${matches.length} users with same credentials found")
      throw new DuplicateEntryFound
    }

    matches.headOption
  }

  @throws[DuplicateEntryFound]
  def handleAvailable(handle: String) = {
    val matches = usersCollection.find(and(equal("handle", handle))).results()

    if (matches.length > 1) {
      logger.error(s"Inconsistent state: ${matches.length} users with same handle found")
      throw new DuplicateEntryFound
    }

    matches.isEmpty
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

    try {
      if (handleAvailable(userSpec.handle))
        usersCollection.insertOne(newUser).results()

      getById(newUser._id)
    } catch {
      case _: DuplicateEntryFound => {
        logger.error(s"Something wrong happened. Two users with the same hande ${userSpec.handle}")
        None
      }
    }
  }

  def getById(userId: String) =
    usersCollection.find(equal("_id", userId)).first().results().headOption

  def deleteUsers(users: Option[List[String]]) = {
    val query = users match {
      case Some(userIds) => usersCollection.deleteMany(in("_id", userIds: _*))
      case None => usersCollection.drop()
    }

    val res = query.results().headOption
    res match {
      case Some(deleteResult: DeleteResult) => deleteResult.getDeletedCount() > 0
      case Some(_: Completed) => true
      case _ => false
    }
  }

  def updateDailyCap(userId: String, dailyCap: Int) =
    usersCollection.updateOne(equal("_id", userId), set("dailyCap", dailyCap)).results().headOption

  def updateUser(userId: String, userSpec: UserSpec) =
    usersCollection.replaceOne(equal("_id", userId), User(userId, userSpec)).results().headOption

  def deleteUser(userId: String) = usersCollection.deleteOne(equal("_id", userId)).results().headOption

  def getAllUsers = usersCollection.find().results
}
