package al.koop.crypto.binance

import java.net.URI

import al.koop.crypto.binance.BinanceWebsocketEvents.{DepthEvent, KlineEvent, TradeEvent}
import al.koop.websocket.rx.{Message, WebSocket}
import org.java_websocket.drafts.Draft_6455
import io.circe.parser.decode
import rx.lang.scala.Observable

class BinanceWebsocketClient private (private val baseUrl: String, private val currencyPair: String) {
  def depth(): Observable[DepthEvent] =
    WebSocket.observe(new URI(s"$baseUrl/$currencyPair@depth"), new Draft_6455())
      .filter {
        case Message(_) => true
        case _ => false
      }
      .map { case Message(m) => decode[DepthEvent](m) }
      .map { case Right(event) => event }
      .share

  def kline(interval: String): Observable[KlineEvent] =
    WebSocket.observe(new URI(s"$baseUrl/$currencyPair@kline_$interval"), new Draft_6455())
      .filter {
        case Message(_) => true
        case _ => false
      }
      .map { case Message(m) => decode[KlineEvent](m) }
      .map { case Right(event) => event }
      .share

  def trades(): Observable[TradeEvent] =
    WebSocket.observe(new URI(s"$baseUrl/$currencyPair@aggTrade"), new Draft_6455())
      .filter {
        case Message(_) => true
        case _ => false
      }
      .map { case Message(m) => decode[TradeEvent](m) }
      .map { case Right(event) => event }
      .share
}

object BinanceWebsocketClient {
  def apply(currencyPair: String) =
    new BinanceWebsocketClient("wss://stream.binance.com:9443/ws", currencyPair)
}
