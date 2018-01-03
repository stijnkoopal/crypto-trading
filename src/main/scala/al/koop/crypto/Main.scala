package al.koop.crypto

import al.koop.crypto.binance.BinanceWebsocketClient
import al.koop.crypto.binance.BinanceWebsocketEvents.TradeEvent
import org.knowm.xchange.binance.BinanceExchange
import org.knowm.xchange.currency.CurrencyPair
import org.knowm.xchange.dto.Order.OrderType
import org.knowm.xchange.dto.trade.LimitOrder
import org.slf4j.LoggerFactory
import rx.lang.scala.Observable

import scala.concurrent.duration._
import org.knowm.xchange.ExchangeFactory

import scala.util.Try

object Main {
  def main(args: Array[String]): Unit = {

    val client = BinanceWebsocketClient("ethbtc")
    client.trades()

//    new PercentChangeTrigger(timeSpan = 20 seconds, percentChange = 0.1)(client.trades())
//      .subscribe(println(_))

    new BelowPriceTrigger(0.07)(client.trades())
      .subscribe(x => {
        println(s"Sell ETHBTC on price ${x.price}")

        println(Order.placeOrder(OrderType.ASK, CurrencyPair.ETH_BTC, price = x.price, amount = BigDecimal(4)))
      })

    new AbovePriceTrigger(0.05)(client.trades())
      .subscribe(x => {
        println(s"Buy ETHBTC on price ${x.price}")

        println(Order.placeOrder(OrderType.ASK, CurrencyPair.ETH_BTC, price = x.price, amount = BigDecimal(4)))
      })
  }
}

class PercentChangeTrigger(val timeSpan: Duration, percentChange: Double)  {
  private val logger = LoggerFactory.getLogger(getClass)

  private val openings: Observable[Long] = Observable.interval(0 seconds, 2 seconds)
  private val closings = (_: Long) => Observable.timer(timeSpan)

  def apply(trades: Observable[TradeEvent]): Observable[TradeEvent] =
    trades.slidingBuffer(openings)(closings)
      .map(window => (window.head, window.last))
      .doOnEach(x => logger.debug(s"Checking start ${x._1}, end ${x._2}") )
      .filter { case (start, end) => calculateChange(start, end) > percentChange}
      .map { case (_, end) => end }

  private def calculateChange(start: TradeEvent, end: TradeEvent) = (end.price / start.price) - 1
}

class PriceComparisonTrigger(val test: BigDecimal => Boolean) {
  def apply(trades: Observable[TradeEvent]): Observable[TradeEvent] =
    trades.filter(trade => test(trade.price))
}

class BelowPriceTrigger(val value: BigDecimal) extends PriceComparisonTrigger(price => price < value)
class AbovePriceTrigger(val value: BigDecimal) extends PriceComparisonTrigger(price => price > value)

object Order {
  val exSpec = new BinanceExchange().getDefaultExchangeSpecification
  exSpec.setUserName("")
  exSpec.setApiKey("")
  exSpec.setSecretKey("")
  val binance = ExchangeFactory.INSTANCE.createExchange(exSpec)

  def placeOrder(orderType: OrderType, pair: CurrencyPair, price: BigDecimal, amount: BigDecimal): Try[String] = {
    Try {
      val order = new LimitOrder(orderType, amount.bigDecimal, pair, null, null, price.bigDecimal)
      binance.getTradeService.placeLimitOrder(order)
    }
  }
}