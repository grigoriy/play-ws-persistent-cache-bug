package com.galekseev.play.ws.bug

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.ehcache.core.EhcacheManager
import org.ehcache.xml.XmlConfiguration
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import play.api.libs.ws.ahc.cache._

import scala.util.{Failure, Success}

object Main extends App{
  import scala.concurrent.ExecutionContext.Implicits._

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  val wsClient: StandaloneAhcWSClient = {
    class EhcacheHttpCache extends Cache {
      private val underlying = {
        val config = new XmlConfiguration(getClass.getResource("/ehcache.xml"))
        val cacheManager = new EhcacheManager(config)
        cacheManager.init()
        cacheManager.getCache("test", classOf[EffectiveURIKey], classOf[ResponseEntry])
      }

      override def remove(key: EffectiveURIKey): Unit =
        underlying.remove(key)
      override def put(key: EffectiveURIKey, entry: ResponseEntry): Unit =
        underlying.put(key, entry)
      override def get(key: EffectiveURIKey): ResponseEntry =
        underlying.get(key)
      override def close(): Unit =
        underlying.clear()
    }
    StandaloneAhcWSClient(httpCache = Some(new AhcHttpCache(new EhcacheHttpCache())))
  }

  wsClient
    .url("http://www.google.com")
    .get()
    .andThen { case _ => wsClient.close() }
    .andThen { case _ => system.terminate() }
    .onComplete {
      case Success(response) => println(s"Got a response: ${response.status}, ${response.body}")
      case Failure(exception) => println(s"Got an exception:\n$exception")
    }
}
