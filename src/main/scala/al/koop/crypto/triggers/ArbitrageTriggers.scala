package al.koop.crypto.triggers

import al.koop.crypto.Crypto.Percentage
import al.koop.crypto.PriceChangeEvent
import org.knowm.xchange.dto.Order.OrderType
import rx.lang.scala.Observable

class ExchangePriceDifferenceTrigger(val difference: Percentage, orderType: OrderType) {
  def apply(priceChanges1: Observable[PriceChangeEvent], priceChanges2: Observable[PriceChangeEvent]) =
    priceChanges1.combineLatest(priceChanges2)
    .filter { case (a, b) => calculateChange(a, b) > difference }

  private def calculateChange(a: PriceChangeEvent, b: PriceChangeEvent) = {
    if (a.price(orderType) > b.price(orderType))
      (a.price(orderType) / b.price(orderType)) - 1
    else
      (b.price(orderType) / a.price(orderType)) - 1
  }

}
