package al.koop.crypto.triggers

import al.koop.crypto.Crypto.{Percentage, Price}
import al.koop.crypto.PriceChangeEvent
import org.knowm.xchange.dto.Order.OrderType
import rx.lang.scala.Observable

class TrailingStopTrigger(val percentageFromTop: Percentage, orderType: OrderType) {
  def apply(priceChanges: Observable[PriceChangeEvent]): Observable[PriceChangeEvent] = {
    val max = priceChanges.map(_.price(orderType))
      .scan(BigDecimal(0)){ case (acc, value) => acc.max(value)}

    priceChanges.combineLatest(max)
      .filter { case (trade, maxPrice) => trade.price(orderType) < maxPrice * (1-percentageFromTop)}
      .map { case (trade, _) => trade }
  }
}

class TrailingStopFromTrigger(val percentageFromTop: Percentage, val triggerAbovePrice: Price, val orderType: OrderType) {
  def apply(priceChanges: Observable[PriceChangeEvent]): Observable[PriceChangeEvent] = {
    val max = priceChanges.map(_.price(orderType))
      .scan(BigDecimal(0)){ case (acc, value) => acc.max(value)}

    priceChanges.combineLatest(max)
      .filter { case (trade, _) => trade.price(orderType) > triggerAbovePrice }
      .filter { case (trade, maxPrice) => trade.price(orderType) < maxPrice * (1-percentageFromTop)}
      .map { case (trade, _) => trade }
  }
}