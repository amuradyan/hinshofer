import Hinshofer._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.HttpOrigin
import akka.http.scaladsl.server.Directives.{entity, _}
import akka.http.scaladsl.unmarshalling.{FromStringUnmarshaller, Unmarshaller}
import com.google.gson.Gson
import pdi.jwt.JwtClaim
import tokens._
import users.{DuplicateEntryFound, User, UserManagement, UserSearchCriteria, UserSpec}

import scala.concurrent.Future

/**
  * Created by spectrum on 6/10/2018.
  */
trait CsvParameters {
  implicit def csvSeqParamMarshaller: FromStringUnmarshaller[Seq[String]] =
    Unmarshaller(ex => s => Future.successful(s.split(",")))

  implicit def csvListParamMarshaller: FromStringUnmarshaller[List[String]] =
    Unmarshaller(ex => s => Future.successful(s.split(",").toList))
}

final object CsvParameters extends CsvParameters

trait Paths {
  val route = {
    var jwtClaim: JwtClaim = null
    var origin = HttpOrigin("http://a.com")
    var token = ""
    val contentType = ContentTypes.`application/json`

    extractRequestContext {
      rc => {
        val originHeader = rc.request.getHeader("Origin")

        if (originHeader.isPresent)
          origin = HttpOrigin(originHeader.get().value())

        pathSingleSlash {
          complete("It's alive!!!")
        } ~
          pathPrefix("tokens") {
            corsHandler(origin) {
              pathEnd {
                post {
                  entity(as[String]) {
                    loginSpecJson => {
                      val loginSpec = new Gson().fromJson(loginSpecJson, classOf[LoginSpec])

                      if (loginSpec.isValid) {
                        try {
                          val fetchRes = UserManagement.getByHandleAndPassword(loginSpec.handle, loginSpec.passwordHash)

                          fetchRes match {
                            case Some(u) => {
                              val token = TokenManagement.issueToken(u)
                              logger.info(s"${loginSpec.handle} logged in")
                              val res = new Gson().toJson(BareToken(token))
                              complete(HttpResponse(StatusCodes.Created, entity = HttpEntity(contentType, res)))
                            }
                            case None =>
                              complete(HttpResponse(StatusCodes.UnprocessableEntity, entity = HttpEntity(contentType, "Invalid username/password")))
                          }
                        } catch {
                          case e: DuplicateEntryFound =>
                            complete(HttpResponse(StatusCodes.InternalServerError, entity = HttpEntity(contentType, e.getLocalizedMessage)))
                        }
                      } else {
                        complete(HttpResponse(StatusCodes.UnprocessableEntity, entity = HttpEntity(contentType, "Invalid username/password")))
                      }
                    }
                  }
                }
              }
            }
          } ~
          pathPrefix("users") {
            pathEnd {
              corsHandler(origin) {
                post {
                  entity(as[String]) {
                    userSpecJson => {
                      logger.info(userSpecJson)
                      val userSpec = new Gson().fromJson(userSpecJson, classOf[UserSpec])

                      if (!userSpec.isValid)
                        complete(HttpResponse(status = StatusCodes.UnprocessableEntity, entity = "Invalid user spec"))
                      else {
                        val newUser = UserManagement.createUser(userSpec)

                        newUser match {
                          case Some(u) => {
                            val res = new Gson().toJson(u)
                            complete(HttpResponse(status = StatusCodes.Created, entity = HttpEntity(contentType, res)))
                          }
                          case None => complete(HttpResponse(StatusCodes.InternalServerError, entity = "Unable to create a user"))
                        }
                      }
                    }
                  }
                }
              }
            }
          } ~
          authorize(rc => {
            val authHeader = rc.request.getHeader("Authorization")

            logger.info("About to auth")
            if (authHeader.isPresent) {
              token = authHeader.get().value()

              logger.info(s"user_management.Token: $token")
              logger.info(s"Is blacklisted: ${TokenManagement.isTokenBlacklisted(token)}")
              logger.info(s"Is valid: ${TokenManagement.isValid(token)}")

              jwtClaim = TokenManagement.decode(token)
              !TokenManagement.isTokenBlacklisted(token) && TokenManagement.isValid(token)
            } else {
              if (rc.request.method.equals(HttpMethods.OPTIONS)) {
                logger.error("Auth header not present but request type options")
                true
              } else {
                logger.error("Auth header not present")
                false
              }
            }
          }) {
            pathPrefix("tokens") {
              corsHandler(origin) {
                delete {
                  TokenManagement.blacklistToken(token)
                  val sub = jwtClaim.subject.getOrElse("")

                  complete(HttpResponse(entity = HttpEntity(contentType, s"${sub} logged out")))
                }
              }
            } ~
              pathPrefix("users") {
                pathEnd {
                  corsHandler(origin) {
                    get {
                      import CsvParameters._

                      parameters('users.as[List[String]].?) {
                        (users) => {
                          val allUsers = UserManagement.getUsers(UserSearchCriteria(users))

                          val res = new Gson().toJson(allUsers.toArray)
                          complete(HttpResponse(entity = HttpEntity(contentType, res)))
                        }
                      }
                    }
                  }
                } ~
                  pathPrefix("me") {
                    corsHandler(origin) {
                      pathEnd {
                        get {
                          val sub = jwtClaim.subject.getOrElse("")

                          UserManagement.getById(sub) match {
                            case Some(user) => {
                              val res = new Gson().toJson(user)
                              complete(HttpResponse(entity = HttpEntity(contentType, res)))
                            }
                            case None =>
                              complete(HttpResponse(StatusCodes.NotFound, entity = HttpEntity(contentType, s"Unable to find user $sub")))
                          }
                        }
                      }
                    }
                  } ~
                  pathPrefix(Segment) {
                    userId => {
                      pathEnd {
                        corsHandler(origin) {
                          get {
                            UserManagement.getById(userId) match {
                              case Some(user) => {
                                val res = new Gson().toJson(user)
                                complete(HttpResponse(entity = HttpEntity(contentType, res)))
                              }
                              case None =>
                                complete(HttpResponse(StatusCodes.NotFound, entity = HttpEntity(contentType, s"Unable to find user $userId")))
                            }
                          }
                        } ~
                          corsHandler(origin) {
                            delete {
                              val deleteRes = UserManagement.deleteUser(userId)

                              val deleted = deleteRes match {
                                case Some(v) => v.getDeletedCount > 0
                                case None => false
                              }

                              if (deleted)
                                complete(HttpResponse(entity = HttpEntity(contentType, s"User $userId deleted")))
                              else
                                complete(HttpResponse(StatusCodes.InternalServerError, entity = HttpEntity(contentType, s"Unable to delete $userId")))
                            }
                          }
                      }
                    }
                  }
              } ~
              pathPrefix("prisms") {
                pathEnd {
                  corsHandler(origin) {
                    get {
                      complete(HttpResponse(StatusCodes.OK))
                    }
                  } ~
                    corsHandler(origin) {
                      post {
                        complete(HttpResponse(StatusCodes.OK))
                      }
                    }
                } ~
                  pathPrefix(Segment) {
                    prismId => {
                      pathEnd {
                        corsHandler(origin) {
                          delete {
                            complete(HttpResponse(StatusCodes.OK))
                          }
                        } ~
                          corsHandler(origin) {
                            patch {
                              complete(HttpResponse(StatusCodes.OK))
                            }
                          }
                      }
                    }
                  }
              }
          }
      }
    }
  }
}
