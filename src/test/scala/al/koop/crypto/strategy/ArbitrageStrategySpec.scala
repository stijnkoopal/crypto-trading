package al.koop.crypto.strategy

import java.time.LocalDateTime
import java.util.Date
import java.util.concurrent.TimeUnit

import org.knowm.xchange.{Exchange, ExchangeSpecification}
import org.knowm.xchange.currency.{Currency, CurrencyPair}
import org.knowm.xchange.dto.Order
import org.knowm.xchange.dto.Order.OrderStatus
import org.knowm.xchange.dto.Order.OrderType._
import org.knowm.xchange.dto.account.{AccountInfo, Balance, Wallet}
import org.knowm.xchange.dto.trade.LimitOrder
import org.knowm.xchange.service.account.AccountService
import org.knowm.xchange.service.trade.TradeService
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}
import org.mockito.Mockito.when
import org.mockito.{Matchers => MockitoMatchers}
import rx.{Scheduler, Subscription}
import rx.functions.Action0
import rx.lang.scala.Observable
import rx.plugins.RxJavaHooks
import rx.subscriptions.Subscriptions

import scala.collection.JavaConverters._

class ArbitrageStrategySpec extends FlatSpec with Matchers with MockitoSugar {
  val base = new Currency("TST")
  val counter = new Currency("ETH")
  val pair = new CurrencyPair(base, counter)

  val buyTradeService = mock[TradeService]
  when(buyTradeService.placeLimitOrder(MockitoMatchers.any())).thenReturn("buy-id")
  when(buyTradeService.getOrder(MockitoMatchers.anyString())).thenReturn(List[Order](new LimitOrder(BID, BigDecimal(0).bigDecimal, pair, "buy-id", new Date(), BigDecimal(0).bigDecimal, BigDecimal(0).bigDecimal, BigDecimal(0).bigDecimal, OrderStatus.FILLED)).asJava)

  val buyWalletMock = new Wallet("buy-wallet", "buy-wallet", Seq[Balance]().asJava)

  val buyAccountInfoMock = new AccountInfo("sell-user", Seq(buyWalletMock).asJava)

  val buyAccountService = mock[AccountService]
  when(buyAccountService.requestDepositAddress(MockitoMatchers.any())).thenReturn(s"deposit-$counter")
  when(buyAccountService.withdrawFunds(MockitoMatchers.any(), MockitoMatchers.any(), MockitoMatchers.anyString())).thenReturn(s"withdraw-transactions-$base")
  when(buyAccountService.getAccountInfo).thenReturn(buyAccountInfoMock)

  val buyExchangeSpecification = mock[ExchangeSpecification]
  when(buyExchangeSpecification.getExchangeName).thenReturn("buy-exchange")

  val buyExchange = mock[Exchange]
  when(buyExchange.getTradeService).thenReturn(buyTradeService)
  when(buyExchange.getAccountService).thenReturn(buyAccountService)
  when(buyExchange.getExchangeSpecification).thenReturn(buyExchangeSpecification)

  val sellTradeService = mock[TradeService]
  when(sellTradeService.placeLimitOrder(MockitoMatchers.any())).thenReturn("sell-id")
  when(sellTradeService.getOrder(MockitoMatchers.anyString())).thenReturn(List[Order](new LimitOrder(ASK, BigDecimal(0).bigDecimal, pair, "sell-id", new Date(), BigDecimal(0).bigDecimal, BigDecimal(0).bigDecimal, BigDecimal(0).bigDecimal, OrderStatus.FILLED)).asJava)

  val sellWalletMock = new Wallet("sell-wallet", "sell-wallet", Seq[Balance]().asJava)

  val sellAccountInfoMock = new AccountInfo("sell-user", Seq(sellWalletMock).asJava)

  val sellAccountService = mock[AccountService]
  when(sellAccountService.requestDepositAddress(MockitoMatchers.any())).thenReturn(s"deposit-$base")
  when(sellAccountService.withdrawFunds(MockitoMatchers.any(), MockitoMatchers.any(), MockitoMatchers.anyString())).thenReturn(s"withdraw-transactions-$counter")
  when(sellAccountService.getAccountInfo).thenReturn(sellAccountInfoMock)

  val sellExchangeSpecification = mock[ExchangeSpecification]
  when(sellExchangeSpecification.getExchangeName).thenReturn("sell-exchange")

  val sellExchange = mock[Exchange]
  when(sellExchange.getTradeService).thenReturn(sellTradeService)
  when(sellExchange.getAccountService).thenReturn(sellAccountService)
  when(sellExchange.getExchangeSpecification).thenReturn(sellExchangeSpecification)

  val detector = mock[ArbitrageDetector]
  when(detector.detect()).thenReturn(Observable.just(DetectedArbitrage(LocalDateTime.now(), pair, ask = 0.5, buyExchange, bid = 1, sellExchange, difference = 2)))

  val balances = new CashedBalancesContainer()
  balances.setBalance(buyExchange, counter, new Balance(counter, BigDecimal(50).bigDecimal))
  balances.setBalance(buyExchange, base, new Balance(base, BigDecimal(50).bigDecimal))
  balances.setBalance(sellExchange, counter, new Balance(counter, BigDecimal(50).bigDecimal))
  balances.setBalance(sellExchange, base, new Balance(base, BigDecimal(50).bigDecimal))

  val tradeFees: Map[(Exchange, CurrencyPair), BigDecimal] = Map(
    (buyExchange, pair) -> 0.1,
    (sellExchange, pair) -> 0.1,
  )

  val withdrawalFees: Map[(Exchange, Currency), BigDecimal] = Map(
    (buyExchange, base) -> 10,
    (sellExchange, counter) -> 10,
  )

  val strategy = new ArbitrageStrategy(detector, tradeFees, withdrawalFees, balances, paperTrade = false, priceSlackPercentage = 0)

  RxJavaHooks.setOnComputationScheduler(_ => () => new Scheduler.Worker {
      override def schedule(action: Action0): Subscription = {
        action.call()
        Subscriptions.unsubscribed
      }

      override def schedule(action: Action0, delayTime: Long, unit: TimeUnit): Subscription = this.schedule(action)
      override def unsubscribe(): Unit = ()
      override def isUnsubscribed: Boolean = true
    })

  "ArbitrageStrategy" should " buy and sell, and wait for orders" in {
    strategy.start()
      .toBlocking
      .single

    balances.getBalance(buyExchange, base).get.getAvailable.doubleValue() shouldBe 65.45454545454545
    balances.getBalance(buyExchange, counter).get.getAvailable.doubleValue() shouldBe 42.5
    balances.getBalance(sellExchange, counter).get.getAvailable.doubleValue() shouldBe 42.5
    balances.getBalance(sellExchange, base).get.getAvailable.doubleValue() shouldBe 65.45454545454545
  }
}
