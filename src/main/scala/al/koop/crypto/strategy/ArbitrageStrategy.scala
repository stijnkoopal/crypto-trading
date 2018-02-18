package al.koop.crypto.strategy

import org.knowm.xchange.Exchange
import org.knowm.xchange.currency.{Currency, CurrencyPair}
import org.knowm.xchange.dto.Order.{OrderStatus, OrderType}
import org.knowm.xchange.dto.account.{AccountInfo, Balance, Wallet}
import org.knowm.xchange.dto.trade.LimitOrder
import org.knowm.xchange.service.account.AccountService
import org.knowm.xchange.service.trade.TradeService
import org.slf4j.LoggerFactory
import rx.Scheduler
import rx.lang.scala.Observable
import rx.schedulers.Schedulers

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.duration._

sealed trait State
case object Available extends State
case object VerifyingDetection extends State
case object Trading extends State
case object TradeDone extends State
case object Exchanging extends State

case class ArbitrageTrade(buyOrder: LimitOrder,
                          sellOrder: LimitOrder,
                          buyFrom: Exchange,
                          sellFrom: Exchange,
                          buyAmount: BigDecimal,
                          sellAmount: BigDecimal,
                          arbitrage: DetectedArbitrage
                         )

case class InTrade(buyOrderId: String, sellOrderId: String, trade: ArbitrageTrade)
case class Exchanging(trade: ArbitrageTrade, baseAmount: BigDecimal, counterAmount: BigDecimal)

trait BalancesContainer {
  def getBalance(wallet: Wallet, currency: Currency): Option[Balance]

  def subtract(wallet: Wallet, currency: Currency, amount: BigDecimal): Unit
  def add(wallet: Wallet, currency: Currency, amount: BigDecimal): Unit
}

class DirectToExchangeBalancesContainer extends BalancesContainer {
  override def getBalance(wallet: Wallet, currency: Currency): Option[Balance] =
    Some(wallet.getBalance(currency))

  override def subtract(wallet: Wallet, currency: Currency, amount: BigDecimal): Unit = ()
  override def add(wallet: Wallet, currency: Currency, amount: BigDecimal): Unit = ()
}

class CashedBalancesContainerWithFallback extends BalancesContainer {
  private val container = new CashedBalancesContainer()
  private val toExchange = new DirectToExchangeBalancesContainer

  def prefillCache(exchange: Exchange*): Unit = container.prefillCache(exchange: _*)

  override def getBalance(wallet: Wallet, currency: Currency): Option[Balance] = {
    container.getBalance(wallet, currency).orElse(toExchange.getBalance(wallet, currency))
  }

  def setBalance(wallet: Wallet, currency: Currency, balance: Balance): Unit =
    container.setBalance(wallet, currency, balance)

  override def subtract(wallet: Wallet, currency: Currency, amount: BigDecimal): Unit =
    container.subtract(wallet, currency, amount)

  override def add(wallet: Wallet, currency: Currency, amount: BigDecimal): Unit =
    container.add(wallet, currency, amount)
}

class CashedBalancesContainer extends BalancesContainer {
  private val cache: mutable.Map[(Wallet, Currency), Balance] = mutable.Map()

  def prefillCache(exchange: Exchange*): Unit = {
    exchange.foreach(exchange => {
      val wallet = exchange.getAccountService.getAccountInfo.getWallet
      val balances = wallet.getBalances.asScala
      balances.foreach(x => cache.put((wallet, x._1), x._2))
    })
  }

  override def getBalance(wallet: Wallet, currency: Currency): Option[Balance] = {
    cache.get((wallet, currency))
  }

  def setBalance(wallet: Wallet, currency: Currency, balance: Balance): Unit = cache.put((wallet, currency), balance)

  override def subtract(wallet: Wallet, currency: Currency, amount: BigDecimal): Unit = {
    val balance = getBalance(wallet, currency)
    cache.put((wallet, currency), new Balance(currency, balance.getOrElse(new Balance(currency, BigDecimal(0).bigDecimal)).getTotal.subtract(amount.bigDecimal)))
  }

  override def add(wallet: Wallet, currency: Currency, amount: BigDecimal): Unit = {
    val balance = getBalance(wallet, currency)
    cache.put((wallet, currency), new Balance(currency, balance.getOrElse(new Balance(currency, BigDecimal(0).bigDecimal)).getTotal.add(amount.bigDecimal)))
  }
}

class ArbitrageStrategy(val detector: ArbitrageDetector,
                        val tradeFeesPercent: Map[(Exchange, CurrencyPair), BigDecimal],
                        val withdrawalFeesStatic: Map[(Exchange, Currency), BigDecimal],
                        val balances: BalancesContainer = new DirectToExchangeBalancesContainer,
                        val paperTrade: Boolean = true,
                        val priceSlackPercentage: BigDecimal = 0.002)
                       (implicit scheduler: Scheduler = Schedulers.computation()) {

  private val logger = LoggerFactory.getLogger(getClass)
  private val state: mutable.Map[CurrencyPair, State] = mutable.Map()

  def start(): Observable[ArbitrageTrade] = {
    detector.detect()
      .filter(arbitrageTrade => this.state.getOrElse(arbitrageTrade.currencyPair, Available) == Available)
      .doOnEach(x => this.state.put(x.currencyPair, VerifyingDetection))
      .map(this.buildTrade)
      .map(this.trade)
      .doOnEach(x => this.state.put(x.trade.arbitrage.currencyPair, Trading))
      .flatMap(a => Observable.timer(5 seconds).map(_ => a))
      .map(this.checkTrades)
      .doOnEach { x => this.state.put(x.arbitrage.currencyPair, TradeDone) }
      .doOnEach { x => this.updateBalancesForTrade(x) }
      .map(this.exchangeCurrencies)
      .doOnEach { x => this.updateBalancesStartExchange(x) }
      .doOnEach { x => this.state.put(x.trade.arbitrage.currencyPair, Exchanging) }
      .flatMap(x => Observable.timer(1 hour).map(_ => x))
      .doOnEach { x => this.updateBalancesEndExchange(x) }
      .doOnEach { x => this.state.put(x.trade.arbitrage.currencyPair, Available) }
      .map(_.trade)
  }

  private def buildTrade(detectedArbitrage: DetectedArbitrage): ArbitrageTrade = {
    val buyPrice = detectedArbitrage.ask * (1 + priceSlackPercentage)
    val buyTradeFee = tradeFeesPercent((detectedArbitrage.buyFrom, detectedArbitrage.currencyPair))
    val buyAmount = (availableBalance(detectedArbitrage.buyFrom, detectedArbitrage.currencyPair.counter) / (1 + buyTradeFee) ) / buyPrice

    val buyOrder = new LimitOrder(
      OrderType.BID,
      buyAmount.bigDecimal,
      detectedArbitrage.currencyPair,
      null,
      null,
      buyPrice.bigDecimal
    )

    val sellPrice = detectedArbitrage.bid * (1 - priceSlackPercentage)
    val sellAmount = availableBalance(detectedArbitrage.sellFrom, detectedArbitrage.currencyPair.base)

    val sellOrder = new LimitOrder(
      OrderType.ASK,
      sellAmount.bigDecimal,
      detectedArbitrage.currencyPair,
      null,
      null,
      sellPrice.bigDecimal
    )

    val trade = ArbitrageTrade(
      buyOrder,
      sellOrder,
      detectedArbitrage.buyFrom,
      detectedArbitrage.sellFrom,
      buyAmount,
      sellAmount,
      detectedArbitrage
    )

    trade
  }

  private def trade(trade: ArbitrageTrade): InTrade = {
    logger.info(s"Trading ${trade.buyOrder.getCurrencyPair}")
    logger.debug(s"Buy order ${trade.buyOrder}, buy on ${trade.buyFrom}")
    logger.debug(s"Sell order ${trade.sellOrder}, sell on ${trade.sellFrom}")

    val buyOrderId = if (paperTrade) "paper" else getTradeService(trade.buyFrom).placeLimitOrder(trade.buyOrder)
    val sellOrderId = if (paperTrade) "paper" else getTradeService(trade.sellFrom).placeLimitOrder(trade.sellOrder)

    InTrade(buyOrderId, sellOrderId, trade)
  }

  private def checkTrades(inTrade: InTrade) = {
    if (!paperTrade) {
      val buyOrder = getTradeService(inTrade.trade.buyFrom).getOrder(inTrade.buyOrderId).asScala.headOption
      val sellOrder = getTradeService(inTrade.trade.sellFrom).getOrder(inTrade.sellOrderId).asScala.headOption

      if (buyOrder.isEmpty || sellOrder.isEmpty) {
        logger.info(inTrade.toString)
        throw new Exception(s"In invalid state, orders could not be found for ${inTrade.trade.arbitrage.currencyPair}")
      }

      if (buyOrder.get.getStatus != OrderStatus.FILLED || sellOrder.get.getStatus != OrderStatus.FILLED) {
        logger.info(inTrade.toString)
        throw new Exception(s"In invalid state, orders not filled for ${inTrade.trade.arbitrage.currencyPair}")
      }
    }

    inTrade.trade
  }

  private def exchangeCurrencies(trade: ArbitrageTrade) = {
    val pair = trade.sellOrder.getCurrencyPair
    val baseAmount = availableBalance(trade.buyFrom, pair.base)
    val counterAmount = availableBalance(trade.sellFrom, pair.counter)

    val baseWithdrawalFee = withdrawalFeesStatic((trade.buyFrom, pair.base))
    val baseWithdrawalAmount = (baseAmount + baseWithdrawalFee) / 2

    val counterWithdrawalFee = withdrawalFeesStatic((trade.sellFrom, pair.counter))
    val counterWithdrawalAmount = (counterAmount + counterWithdrawalFee) / 2

    if (!paperTrade) {
      val counterDepositAddress = getAccountService(trade.buyFrom).requestDepositAddress(pair.counter)
      val baseDepositAddress = getAccountService(trade.sellFrom).requestDepositAddress(pair.base)

      getAccountService(trade.buyFrom).withdrawFunds(pair.base, baseWithdrawalAmount.bigDecimal, baseDepositAddress)
      getAccountService(trade.sellFrom).withdrawFunds(pair.counter, counterWithdrawalAmount.bigDecimal, counterDepositAddress)
    }

    Exchanging(trade, baseWithdrawalAmount, counterWithdrawalAmount)
  }

  protected def availableBalance(exchange: Exchange, currency: Currency): BigDecimal =
    BigDecimal(balances.getBalance(exchange.getAccountService.getAccountInfo.getWallet, currency).map(_.getAvailable).getOrElse(BigDecimal(0).bigDecimal))

  private def updateBalancesForTrade(tradeDone: ArbitrageTrade): Unit = {
    val buyFee = tradeFeesPercent(tradeDone.buyFrom, tradeDone.arbitrage.currencyPair) * (tradeDone.buyAmount * tradeDone.buyOrder.getLimitPrice)
    balances.add(tradeDone.buyFrom.getAccountService.getAccountInfo.getWallet, tradeDone.arbitrage.currencyPair.base, tradeDone.buyAmount )
    balances.subtract(tradeDone.buyFrom.getAccountService.getAccountInfo.getWallet, tradeDone.arbitrage.currencyPair.counter, tradeDone.buyAmount * tradeDone.buyOrder.getLimitPrice + buyFee)

    val sellFee = tradeFeesPercent(tradeDone.sellFrom, tradeDone.arbitrage.currencyPair) * (tradeDone.sellAmount / tradeDone.sellOrder.getLimitPrice)
    balances.subtract(tradeDone.sellFrom.getAccountService.getAccountInfo.getWallet, tradeDone.arbitrage.currencyPair.base, tradeDone.sellAmount)
    balances.add(tradeDone.sellFrom.getAccountService.getAccountInfo.getWallet, tradeDone.arbitrage.currencyPair.counter, tradeDone.sellAmount / tradeDone.sellOrder.getLimitPrice - sellFee)
  }

  private def updateBalancesStartExchange(exchanging: Exchanging): Unit = {
    balances.subtract(exchanging.trade.buyFrom.getAccountService.getAccountInfo.getWallet, exchanging.trade.arbitrage.currencyPair.base, exchanging.baseAmount)
    balances.subtract(exchanging.trade.sellFrom.getAccountService.getAccountInfo.getWallet, exchanging.trade.arbitrage.currencyPair.counter, exchanging.counterAmount)
  }

  private def updateBalancesEndExchange(exchanging: Exchanging): Unit = {
    val baseFee = withdrawalFeesStatic((exchanging.trade.buyFrom, exchanging.trade.arbitrage.currencyPair.base))
    balances.add(exchanging.trade.sellFrom.getAccountService.getAccountInfo.getWallet, exchanging.trade.arbitrage.currencyPair.base, exchanging.baseAmount - baseFee)

    val counterFee = withdrawalFeesStatic((exchanging.trade.sellFrom, exchanging.trade.arbitrage.currencyPair.counter))
    balances.add(exchanging.trade.buyFrom.getAccountService.getAccountInfo.getWallet, exchanging.trade.arbitrage.currencyPair.counter, exchanging.counterAmount - counterFee)
  }

  private def getAccountService(exchange: Exchange): AccountService = exchange.getAccountService
  private def getTradeService(exchange: Exchange): TradeService = exchange.getTradeService
}
