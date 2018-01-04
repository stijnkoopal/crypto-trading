package al.koop.crypto.triggers

import al.koop.crypto.Crypto.Price
import al.koop.crypto.PriceChangeEvent
import rx.lang.scala.Observable

sealed class StaticPriceTrigger(val test: Price => Boolean) {
  def apply(priceChanges: Observable[PriceChangeEvent]): Observable[PriceChangeEvent] =
    priceChanges.filter(trade => test(trade.price))
}

class BelowPriceTrigger(val triggerPrice: Price) extends StaticPriceTrigger(price => triggerPrice < price)
class AbovePriceTrigger(val triggerPrice: Price) extends StaticPriceTrigger(price => price > triggerPrice)