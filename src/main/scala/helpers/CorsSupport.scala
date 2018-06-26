package helpers

/**
  * Created by spectrum on 5/15/2018.
  */

import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers.{`Access-Control-Allow-Credentials`, `Access-Control-Allow-Headers`, `Access-Control-Allow-Methods`, `Access-Control-Allow-Origin`, _}
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.config.ConfigFactory

trait CorsSupport {

  lazy val allowedOrigins = {
    val config = ConfigFactory.load()
    config.getStringList("cors.allowed-origins")
  }

  //this directive adds access control headers to normal responses
  private def addAccessControlHeaders(httpOrigin: HttpOrigin) = {
    var replyOrigin = httpOrigin
    val httpOriginStr = httpOrigin.scheme + "://" + httpOrigin.host.toString().replaceAll("Host: ", "")

    if (!allowedOrigins.contains(httpOriginStr))
      replyOrigin = HttpOrigin("http://rafik.com:8888")

    respondWithDefaultHeaders(
      `Access-Control-Allow-Origin`(replyOrigin),
      `Access-Control-Allow-Credentials`(true),
      `Access-Control-Allow-Headers`("Authorization", "Content-Type", "Cache-Control")
    )
  }

  //this handles preflight OPTIONS requests.
  private def preflightRequestHandler(httpOrigin: HttpOrigin): Route = options {
    var replyOrigin = httpOrigin
    val httpOriginStr = httpOrigin.scheme + "://" + httpOrigin.host.toString().replaceAll("Host: ", "")

    if (!allowedOrigins.contains(httpOriginStr))
      replyOrigin = HttpOrigin("http://rafik.com:8888")

    complete(HttpResponse(StatusCodes.OK)
      .withHeaders(
        `Access-Control-Allow-Origin`(replyOrigin),
        `Access-Control-Allow-Methods`(OPTIONS, POST, GET, DELETE, PATCH)))
  }

  def corsHandler(origin: HttpOrigin)(r: Route) = addAccessControlHeaders(origin) {
    preflightRequestHandler(origin) ~ r
  }
}
