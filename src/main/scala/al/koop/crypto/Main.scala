package al.koop.crypto

import al.koop.crypto.exchanges.binance.BinanceWebsocketClient
import al.koop.crypto.triggers.ExchangePriceDifferenceTrigger
import org.knowm.xchange.binance.BinanceExchange
import org.knowm.xchange.currency.CurrencyPair
import org.knowm.xchange.dto.Order.OrderType
import org.knowm.xchange.dto.trade.LimitOrder
import org.knowm.xchange.ExchangeFactory
import al.koop.crypto.exchanges.binance.Adapters._
import al.koop.crypto.exchanges.bittrex.Adapters._
import al.koop.crypto.exchanges.bittrex.PollingClient
import org.knowm.xchange.bittrex.BittrexExchange

import scala.util.Try
import scala.concurrent.duration._

object Main {
  def main(args: Array[String]): Unit = {
    val binanceWebsocketClient = BinanceWebsocketClient(CurrencyPair.ETH_BTC)
    val bittrexExchange = ExchangeFactory.INSTANCE.createExchange(classOf[BittrexExchange].getName)

    val bittrexTrades = PollingClient(CurrencyPair.ETH_BTC, 1 second)(bittrexExchange)
      .ticker()

    val binanceTrades = binanceWebsocketClient.trades()
    new ExchangePriceDifferenceTrigger(0.02, OrderType.ASK)(bittrexTrades, binanceTrades)
      .subscribe(println(_))

//
//    new PercentChangeTrigger(timeSpan = 20 seconds, percentChange = 0.1, OrderType.ASK)(binanceWebsocketClient.trades())
//      .subscribe(println(_))
////
//    new TrailingStopTrigger(percentageFromTop = 0.01)(binanceWebsocketClient.trades())
//      .subscribe(x => println(x))
//
//    new BelowPriceTrigger(triggerPrice = 0.07)(binanceWebsocketClient.trades())
//      .subscribe(x => {
//        println(s"Sell ETHBTC on price ${x.price}")
//
//        println(Order.placeOrder(OrderType.ASK, CurrencyPair.ETH_BTC, price = x.price, amount = BigDecimal(4)))
//      })
//
//    new AbovePriceTrigger(triggerPrice = 0.05)(binanceWebsocketClient.trades())
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