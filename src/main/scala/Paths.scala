import akka.http.scaladsl.model.headers.HttpOrigin
import akka.http.scaladsl.server.Directives.{complete, extractRequestContext, pathSingleSlash}
import tokens.JWTPayload

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
        }
      }
    }
  }
}
