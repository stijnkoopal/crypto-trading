package al.koop.crypto

import java.time.LocalDateTime

object Crypto {
  type Price = BigDecimal
  type Percentage = Double
}

sealed trait Event {
  val timestamp: LocalDateTime
}

case class PriceChangeEvent(timestamp: LocalDateTime, price: Crypto.Price) extends Event
