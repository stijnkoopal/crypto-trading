package al.koop.crypto.triggers

import al.koop.crypto.Crypto.{Percentage, Price}
import al.koop.crypto.PriceChangeEvent
import rx.lang.scala.Observable

class TrailingStopTrigger(val percentageFromTop: Percentage) {
  def apply(priceChanges: Observable[PriceChangeEvent]): Observable[PriceChangeEvent] = {
    val max = priceChanges.map(_.price)
      .scan(BigDecimal(0)){ case (acc, value) => acc.max(value)}

    Observable.combineLatest(List(priceChanges, max).asInstanceOf[Iterable[Observable[Serializable]]])(list => (list.head.asInstanceOf[PriceChangeEvent], list.last.asInstanceOf[BigDecimal]))
      .filter { case (trade, maxPrice) => trade.price < maxPrice * (1-percentageFromTop)}
      .map { case (trade, _) => trade }
  }
}

class TrailingStopFromTrigger(val percentageFromTop: Percentage, val triggerAbovePrice: Price) {
  def apply(priceChanges: Observable[PriceChangeEvent]): Observable[PriceChangeEvent] = {
    val max = priceChanges.map(_.price)
      .scan(BigDecimal(0)){ case (acc, value) => acc.max(value)}

    Observable.combineLatest(List(priceChanges, max).asInstanceOf[Iterable[Observable[Serializable]]])(list => (list.head.asInstanceOf[PriceChangeEvent], list.last.asInstanceOf[BigDecimal]))
      .filter { case (trade, _) => trade.price > triggerAbovePrice }
      .filter { case (trade, maxPrice) => trade.price < maxPrice * (1-percentageFromTop)}
      .map { case (trade, _) => trade }
  }
}