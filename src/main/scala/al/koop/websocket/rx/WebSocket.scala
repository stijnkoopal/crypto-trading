package al.koop.websocket.rx

import java.net.URI

import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft
import org.java_websocket.handshake.ServerHandshake
import rx.lang.scala.{ Subscription, Observable }

import collection.JavaConverters._

// https://github.com/KadekM/websockets-rx-scala/blob/master/src/main/scala/websockets/rx/SocketMessage.scala

object WebSocket {
  def observe(serverUri: URI,
              draft: Draft,
              headers: Map[String, String] = Map.empty[String, String],
              connectionTimeout: Int = 0,
              transform: WebSocketClient ⇒ Unit = ws ⇒ ()): Observable[SocketMessage] =
    Observable.create[SocketMessage] { observer ⇒
      val socket = new WebSocketClient(serverUri, draft, mapAsJavaMapConverter(headers).asJava, connectionTimeout) {
        override def onError(ex: Exception): Unit = observer.onError(ex)
        override def onMessage(message: String): Unit = observer.onNext(Message(message))
        override def onClose(code: Int, reason: String, remote: Boolean): Unit = {
          observer.onNext(Close(code, reason, remote))
          observer.onNext(Message("YOLO"))
          observer.onCompleted()
        }
        override def onOpen(handshakedata: ServerHandshake): Unit = observer.onNext(Open(handshakedata))
      }

      transform(socket)

      socket.connectBlocking()

      Subscription { socket.closeBlocking() }
    }.publish.refCount
}