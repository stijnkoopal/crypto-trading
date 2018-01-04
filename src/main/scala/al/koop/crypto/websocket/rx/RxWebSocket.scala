package al.koop.crypto.websocket.rx

import java.net.URI

import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft
import org.java_websocket.handshake.ServerHandshake
import rx.lang.scala.{ Observable, Subject }
import scala.collection.JavaConverters._

class RxWebSocket(serverUri: URI,
                  draft: Draft,
                  headers: Map[String, String] = Map.empty[String, String],
                  connectionTimeout: Int = 0) extends WebSocketClient(serverUri, draft, mapAsJavaMapConverter(headers).asJava, connectionTimeout) {

  private val _events = Subject[SocketMessage]()
  val events: Observable[SocketMessage] = _events

  override def onError(ex: Exception): Unit = _events.onError(ex)
  override def onMessage(message: String): Unit = _events.onNext(Message(message))
  override def onClose(code: Int, reason: String, remote: Boolean): Unit = {
    _events.onNext(Close(code, reason, remote))
    _events.onCompleted()
  }

  override def onOpen(handshakedata: ServerHandshake): Unit = _events.onNext(Open(handshakedata))
}