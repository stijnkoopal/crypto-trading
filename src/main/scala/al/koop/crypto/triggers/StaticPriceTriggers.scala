package al.koop.crypto.triggers

import al.koop.crypto.Crypto.Price
import al.koop.crypto.PriceChangeEvent
import org.knowm.xchange.dto.Order.OrderType
import rx.lang.scala.Observable

sealed abstract class StaticPriceTrigger(val test: Price => Boolean) {
  val orderType: OrderType

  def apply(priceChanges: Observable[PriceChangeEvent]): Observable[PriceChangeEvent] =
    priceChanges.filter(trade => test(trade.price(orderType)))
}

class BelowPriceTrigger(val triggerPrice: Price, val orderType: OrderType) extends StaticPriceTrigger(price => triggerPrice < price)
class AbovePriceTrigger(val triggerPrice: Price, val orderType: OrderType) extends StaticPriceTrigger(price => price > triggerPrice)