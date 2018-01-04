package al.koop.crypto

import al.koop.crypto.exchanges.binance.BinanceWebsocketClient
import al.koop.crypto.triggers.{BelowPriceTrigger, PercentChangeTrigger, TrailingStopTrigger}
import org.knowm.xchange.binance.BinanceExchange
import org.knowm.xchange.currency.CurrencyPair
import org.knowm.xchange.dto.Order.OrderType
import org.knowm.xchange.dto.trade.LimitOrder
import org.knowm.xchange.ExchangeFactory
import al.koop.crypto.exchanges.binance.Adapters._

import scala.util.Try
import scala.concurrent.duration._

object Main {
  def main(args: Array[String]): Unit = {
    val client = BinanceWebsocketClient(CurrencyPair.ETH_BTC)

    new PercentChangeTrigger(timeSpan = 20 seconds, percentChange = 0.1)(client.trades())
      .subscribe(println(_))
//
    new TrailingStopTrigger(percentageFromTop = 0.01)(client.trades())
      .subscribe(x => println(x))

    new BelowPriceTrigger(triggerPrice = 0.07)(client.trades())
      .subscribe(x => {
        println(s"Sell ETHBTC on price ${x.price}")

        println(Order.placeOrder(OrderType.ASK, CurrencyPair.ETH_BTC, price = x.price, amount = BigDecimal(4)))
      })
//
//    new AbovePriceTrigger(triggerPrice = 0.05)(client.trades())
//      .subscribe(x => {
//        println(s"Buy ETHBTC on price ${x.price}")
//
//        println(Order.placeOrder(OrderType.ASK, CurrencyPair.ETH_BTC, price = x.price, amount = BigDecimal(4)))
//      })
  }
}

object Order {
  val exSpec = new BinanceExchange().getDefaultExchangeSpecification
  val binance = ExchangeFactory.INSTANCE.createExchange(exSpec)

  def placeOrder(orderType: OrderType, pair: CurrencyPair, price: BigDecimal, amount: BigDecimal): Try[String] = {
    Try {
      val order = new LimitOrder(orderType, amount.bigDecimal, pair, null, null, price.bigDecimal)
      binance.getTradeService.placeLimitOrder(order)
    }
  }
}