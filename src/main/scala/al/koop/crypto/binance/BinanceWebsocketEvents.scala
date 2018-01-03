package al.koop.crypto.binance

import java.util.Date

import io.circe.{Decoder, HCursor}

object BinanceWebsocketEvents {

  case class TradeEvent(eventTime: Date, // E
                        symbol: String, // s
                        aggregatedTradeId: Long, // a
                        price: BigDecimal, // p
                        quantity: BigDecimal, // q
                        firstBreakdownId: Long, // f
                        lastBreakdownId: Long, // l
                        tradeTime: Date, // T
                        maker: Boolean, // m
                       )

  case class KlineEvent(eventTime: Date, // E
                        symbol: String, // s
                        transaction: KlineTransaction // k
                       )
  case class KlineTransaction(startTime: Date, // t
                              endTime: Date, // T
                              symbol: String, // s
                              interval: String, // i
                              priceOpen: BigDecimal, // o
                              priceClose: BigDecimal, // c
                              priceLow: BigDecimal, // l
                              priceHigh: BigDecimal, // h
                              volume: BigDecimal, // v
                              numberOfTrades: Long, // n
                              finalized: Boolean, // x
                             )

  case class DepthDelta(price: BigDecimal, quantity: BigDecimal)

  case class DepthEvent(eventTime: Date, // E
                        symbol: String, // s
                        updateId: Long, // u
                        bidDelta: List[DepthDelta], // b
                        askDelta: List[DepthDelta] // a
                       )

  implicit val decodeIntOrString: Decoder[Either[String, Array[String]]] =
    Decoder[String].map(Left(_)).or(Decoder[Array[String]].map(Right(_)))

  implicit val decodeDepthEvent: Decoder[DepthEvent] = (c: HCursor) =>
    for {
      eventTime <- c.downField("E").as[Long]
      symbol <- c.downField("s").as[String]
      updateId <- c.downField("u").as[Long]
      bidDelta <- c.downField("b").as[List[List[Either[String, Array[String]]]]]
      askDelta <- c.downField("a").as[List[List[Either[String, Array[String]]]]]

    } yield {
      DepthEvent(
        new Date(eventTime),
        symbol,
        updateId,
        bidDelta.map(x => DepthDelta(BigDecimal(x.head.left.get), BigDecimal(x(1).left.get))),
        askDelta.map(x => DepthDelta(BigDecimal(x.head.left.get), BigDecimal(x(1).left.get)))
      )
    }

  implicit val decodeKlineEvent: Decoder[KlineEvent] = (c: HCursor) =>
    for {
      eventTime <- c.downField("E").as[Long]
      symbol <- c.downField("s").as[String]
      transaction <- c.downField("k").as[KlineTransaction]
    } yield {
      KlineEvent(new Date(eventTime), symbol, transaction)
    }

  implicit val decodeKlineTransaction: Decoder[KlineTransaction] = (c: HCursor) =>
    for {
      startTime <- c.downField("t").as[Long]
      endTime <- c.downField("T").as[Long]
      symbol <- c.downField("s").as[String]
      interval <- c.downField("i").as[String]
      priceOpen <- c.downField("o").as[BigDecimal]
      priceClose <- c.downField("c").as[BigDecimal]
      priceLow <- c.downField("l").as[BigDecimal]
      priceHigh <- c.downField("h").as[BigDecimal]
      volume <- c.downField("v").as[BigDecimal]
      numberOfTrades <- c.downField("n").as[Long]
      finalized <- c.downField("x").as[Boolean]
    } yield {
      KlineTransaction(
        new Date(startTime),
        new Date(endTime),
        symbol,
        interval,
        priceOpen,
        priceClose,
        priceLow,
        priceHigh,
        volume,
        numberOfTrades,
        finalized)
    }

  implicit val decodeTradeEvent: Decoder[TradeEvent] = (c: HCursor) =>
    for {
      eventTime <- c.downField("E").as[Long]
      symbol <- c.downField("s").as[String]
      aggregatedTradeId <- c.downField("a").as[Long]
      price <- c.downField("p").as[BigDecimal]
      quantity <- c.downField("q").as[BigDecimal]
      firstBreakdownId <- c.downField("f").as[Long]
      lastBreakdownId <- c.downField("l").as[Long]
      tradeTime <- c.downField("T").as[Long]
      maker <- c.downField("m").as[Boolean]
    } yield {
      TradeEvent(
        new Date(eventTime),
        symbol,
        aggregatedTradeId,
        price,
        quantity,
        firstBreakdownId,
        lastBreakdownId,
        new Date(tradeTime),
        maker)
    }
}
