import Hinshofer._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.HttpOrigin
import akka.http.scaladsl.server.Directives.{entity, _}
import akka.http.scaladsl.unmarshalling.{FromStringUnmarshaller, Unmarshaller}
import com.google.gson.Gson
import tokens._
import users.{User, UserManagement, UserSearchCriteria, UserSpec}

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

/**
  * Created by spectrum on Jun, 2018
  */
trait Paths {
  val route = {
    var payload: JWTPayload = null
    var origin = HttpOrigin("http://a.com")
    var token = ""

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

                      if (!loginSpec.isValid)
                        complete(HttpResponse(StatusCodes.NotFound, entity = HttpEntity("Invalid username/password")))
                      else {
                        val userExists = UserManagement.exists(loginSpec)

                        if (userExists) {
                          val token = TokenManagement.issueToken(loginSpec)

                          logger.info(s"${loginSpec.handle} logged in")
                          complete(HttpResponse(StatusCodes.OK, entity = HttpEntity(ContentTypes.`application/json`, new Gson().toJson(BareToken(token)))))
                        } else {
                          complete(HttpResponse(StatusCodes.NotFound, entity = HttpEntity("Invalid username/password")))
                        }
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
                        complete(HttpResponse(status = StatusCodes.BadRequest, entity = "Invalid user spec"))
                      else {
                        val newUser = UserManagement.createUser(userSpec)

                        newUser match {
                          case Some(u) => {
                            val res = new Gson().toJson(u)
                            complete(HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, res)))
                          }
                          case None => complete(HttpResponse(StatusCodes.NotFound))
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

              payload = TokenManagement.decode(token)
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
                  complete(s"${payload.sub} logged out")
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
                          var allUsers = Seq[User]()
                          allUsers = UserManagement.getUsers(UserSearchCriteria(users))

                          val res = new Gson().toJson(allUsers.toArray)
                          complete(HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, res)))
                        }
                      }
                    }
                  }
                } ~
                  pathPrefix("me") {
                    corsHandler(origin) {
                      pathEnd {
                        get {
                          val jwtPayload = TokenManagement.decode(token)
                          val res = new Gson().toJson(UserManagement.getByUsername(jwtPayload.sub))
                          complete(HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, res)))
                        }
                      }
                    }
                  } ~
                  pathPrefix(Segment) {
                    username => {
                      pathEnd {
                        corsHandler(origin) {
                          get {
                            logger.info(s"User $username accessed by ${payload.sub}")
                            val res = new Gson().toJson(UserManagement.getByUsername(username))

                            complete(HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, res)))
                          }
                        } ~
                          corsHandler(origin) {
                            delete {
                              UserManagement.deleteUser(username)
                              complete(s"User $username deleted")
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
