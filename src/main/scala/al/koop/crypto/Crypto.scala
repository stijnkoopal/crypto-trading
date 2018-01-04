package al.koop.crypto

import java.time.LocalDateTime

import al.koop.crypto.Crypto.Price
import org.knowm.xchange.dto.Order.OrderType

object Crypto {
  type Price = BigDecimal
  type Percentage = Double
}

sealed trait Event {
  val timestamp: LocalDateTime
}

case class PriceChangeEvent(timestamp: LocalDateTime, ask: Price, bid: Price) extends Event {
  def price(orderType: OrderType) = orderType match {
    case OrderType.ASK | OrderType.EXIT_ASK => ask
    case OrderType.BID | OrderType.EXIT_BID => bid
  }
}
