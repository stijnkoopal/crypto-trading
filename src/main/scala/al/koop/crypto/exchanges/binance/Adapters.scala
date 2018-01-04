package al.koop.crypto.exchanges.binance

import al.koop.crypto.PriceChangeEvent
import al.koop.crypto.exchanges.binance.BinanceWebsocketEvents.TradeEvent
import rx.lang.scala.Observable

object Adapters {
  implicit def tradeEvent2PriceEvent(tradeEvent: TradeEvent): PriceChangeEvent = PriceChangeEvent(tradeEvent.tradeTime, tradeEvent.price, tradeEvent.price)
  implicit def tradeEvent2PriceEvent(tradeEvents: Observable[TradeEvent]): Observable[PriceChangeEvent] = tradeEvents.map(tradeEvent2PriceEvent)
}
