import java.io.InputStream
import java.security.{KeyStore, SecureRandom}

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import helpers.CorsSupport
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}

/**
  * Created by spectrum on Jun, 2018
  */

class Hinshofer

object Hinshofer extends App with CorsSupport with Paths {
  val logger = Logger[Hinshofer]

  val conf = ConfigFactory.load()
  val host = conf.getString("app.host")
  val port = conf.getInt("app.port")

  implicit val actorSystem = ActorSystem("Prism")
  implicit val executionCtx = actorSystem.dispatcher
  implicit val materializer = ActorMaterializer()

  val ks: KeyStore = KeyStore.getInstance("PKCS12")

  val keystore: InputStream = getClass.getResourceAsStream("/cert.p12")
  require(keystore != null, "Keystore required!")
  ks.load(keystore, "".toCharArray)

  val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance("SunX509")
  keyManagerFactory.init(ks, "".toCharArray)

  val tmf: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
  tmf.init(ks)

  val sslContext: SSLContext = SSLContext.getInstance("TLS")
  sslContext.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers, new SecureRandom)

  val https: HttpsConnectionContext = ConnectionContext.https(sslContext)

  val bindingFuture: Future[ServerBinding] = null

  val f = for {bindingFuture <- Http().bindAndHandle(route, host, port)
               waitOnFuture <- Promise[Done].future
  } yield waitOnFuture

  logger.info(s"Server online at https://$host:$port/")

  sys.addShutdownHook {
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => actorSystem.terminate())
  }

  Await.ready(f, Duration.Inf)
}
