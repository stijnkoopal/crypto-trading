package al.koop.websocket.rx

import org.java_websocket.handshake.ServerHandshake

sealed trait SocketMessage
final case class Message(message: String) extends SocketMessage
final case class Close(code: Int, reason: String, remote: Boolean) extends SocketMessage
final case class Open(handshakedata: ServerHandshake) extends SocketMessage