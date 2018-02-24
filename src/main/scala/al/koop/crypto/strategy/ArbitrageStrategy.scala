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
  def getBalance(exchange: Exchange, currency: Currency): Option[Balance]

  def subtract(exchange: Exchange, currency: Currency, amount: BigDecimal): Unit
  def add(exchange: Exchange, currency: Currency, amount: BigDecimal): Unit
}

class DirectToExchangeBalancesContainer extends BalancesContainer {
  override def getBalance(exchange: Exchange, currency: Currency): Option[Balance] =
    Some(exchange.getAccountService.getAccountInfo.getWallet().getBalance(currency))

  override def subtract(exchange: Exchange, currency: Currency, amount: BigDecimal): Unit = ()
  override def add(exchange: Exchange, currency: Currency, amount: BigDecimal): Unit = ()
}

class CashedBalancesContainerWithFallback extends BalancesContainer {
  private val container = new CashedBalancesContainer()
  private val toExchange = new DirectToExchangeBalancesContainer

  def prefillCache(exchange: Exchange*): Unit = container.prefillCache(exchange: _*)

  override def getBalance(exchange: Exchange, currency: Currency): Option[Balance] = {
    container.getBalance(exchange, currency).orElse(toExchange.getBalance(exchange, currency))
  }

  def setBalance(exchange: Exchange, currency: Currency, balance: Balance): Unit =
    container.setBalance(exchange, currency, balance)

  override def subtract(exchange: Exchange, currency: Currency, amount: BigDecimal): Unit =
    container.subtract(exchange, currency, amount)

  override def add(exchange: Exchange, currency: Currency, amount: BigDecimal): Unit =
    container.add(exchange, currency, amount)
}

class CashedBalancesContainer extends BalancesContainer {
  private val cache: mutable.Map[(Exchange, Currency), Balance] = mutable.Map()

  def prefillCache(exchange: Exchange*): Unit = {
    exchange.foreach(exchange => {
      val wallet = exchange.getAccountService.getAccountInfo.getWallet
      val balances = wallet.getBalances.asScala
      balances.foreach(x => cache.put((exchange, x._1), x._2))
    })
  }

  override def getBalance(exchange: Exchange, currency: Currency): Option[Balance] = {
    cache.get((exchange, currency))
  }

  def setBalance(exchange: Exchange, currency: Currency, balance: Balance): Unit = cache.put((exchange, currency), balance)

  override def subtract(exchange: Exchange, currency: Currency, amount: BigDecimal): Unit = {
    val balance = getBalance(exchange, currency)
    cache.put((exchange, currency), new Balance(currency, balance.getOrElse(new Balance(currency, BigDecimal(0).bigDecimal)).getTotal.subtract(amount.bigDecimal)))
  }

  override def add(exchange: Exchange, currency: Currency, amount: BigDecimal): Unit = {
    val balance = getBalance(exchange, currency)
    cache.put((exchange, currency), new Balance(currency, balance.getOrElse(new Balance(currency, BigDecimal(0).bigDecimal)).getTotal.add(amount.bigDecimal)))
  }
}

class ArbitrageStrategy(val detector: ArbitrageDetector,
                        val tradeFeesPercent: Map[(Exchange, CurrencyPair), BigDecimal],
                        val withdrawalFeesStatic: Map[(Exchange, Currency), BigDecimal],
                        val balances: BalancesContainer = new DirectToExchangeBalancesContainer,
                        val paperTrade: Boolean = true,
                        val transferTime: Duration = 1 hour,
                        val priceSlackPercentage: BigDecimal = 0.002)
                       (implicit scheduler: Scheduler = Schedulers.computation()) {

  private val logger = LoggerFactory.getLogger(getClass)
  private val state: mutable.Map[CurrencyPair, State] = mutable.Map()

  def start(): Observable[ArbitrageTrade] = {
    logger.info(s"Starting arbitrage strategy(paper=${paperTrade}, priceSlackPercentage=${priceSlackPercentage})")

    detector.detect()
      .filter(arbitrageTrade => this.state.getOrElse(arbitrageTrade.currencyPair, Available) == Available)
      .doOnEach(x => this.state.put(x.currencyPair, VerifyingDetection))
      .map(this.buildTrade)
      .doOnEach(x => this.logBeforeTrade(x))
      .map(this.trade)
      .doOnEach(x => this.state.put(x.trade.arbitrage.currencyPair, Trading))
      .doOnEach(x => this.logTrade(x))
      .flatMap(a => Observable.timer(5 seconds).map(_ => a))
      .map(this.checkTrades)
      .doOnEach { x => this.state.put(x.arbitrage.currencyPair, TradeDone) }
      .doOnEach { x => this.updateBalancesForTrade(x) }
      .doOnEach(x => this.logAfterTrade(x))
      .map(this.exchangeCurrencies)
      .doOnEach { x => this.updateBalancesStartExchange(x) }
      .doOnEach(x => this.logAfterExchangeStart(x))
      .doOnEach { x => this.state.put(x.trade.arbitrage.currencyPair, Exchanging) }
      .flatMap(x => Observable.timer(transferTime).map(_ => x))
      .doOnEach { x => this.updateBalancesEndExchange(x) }
      .doOnEach(x => this.logAfterExchangeEnd(x))
      .doOnEach { x => this.state.put(x.trade.arbitrage.currencyPair, Available) }
      .doOnEach { _ => logger.info("Exchange done") }
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
    val buyOrderId = if (paperTrade) "paper" else getTradeService(trade.buyFrom).placeLimitOrder(trade.buyOrder)
    val sellOrderId = if (paperTrade) "paper" else getTradeService(trade.sellFrom).placeLimitOrder(trade.sellOrder)

    InTrade(buyOrderId, sellOrderId, trade)
  }

  private def logTrade(trade: InTrade): Unit = {
    logger.info(s"Trading ${trade.trade.buyOrder.getCurrencyPair}")
    logger.debug(s"Buy order ${trade.trade.buyOrder}, buy on ${trade.trade.buyFrom}")
    logger.debug(s"Sell order ${trade.trade.sellOrder}, sell on ${trade.trade.sellFrom}")
  }

  private def logBeforeTrade(arbitrageTrade: ArbitrageTrade): Unit = {
    val base = arbitrageTrade.buyOrder.getCurrencyPair.base
    val counter = arbitrageTrade.buyOrder.getCurrencyPair.counter

    val exchange1 = arbitrageTrade.buyFrom
    val exchange2 = arbitrageTrade.sellFrom

    logger.info(s"Balance before trade. ${exchange1}: ${base.getSymbol}: ${this.balances.getBalance(exchange1, base).get.getAvailable}, ${counter.getSymbol}: ${this.balances.getBalance(exchange1, counter).get.getAvailable}")
    logger.info(s"Balance before trade. ${exchange2}: ${base.getSymbol}: ${this.balances.getBalance(exchange2, base).get.getAvailable}, ${counter.getSymbol}: ${this.balances.getBalance(exchange2, counter).get.getAvailable}")
  }

  private def logAfterTrade(arbitrageTrade: ArbitrageTrade): Unit = {
    val base = arbitrageTrade.buyOrder.getCurrencyPair.base
    val counter = arbitrageTrade.buyOrder.getCurrencyPair.counter

    val exchange1 = arbitrageTrade.buyFrom
    val exchange2 = arbitrageTrade.sellFrom

    logger.info(s"Balance after trade. ${exchange1}: ${base.getSymbol}: ${this.balances.getBalance(exchange1, base).get.getAvailable}, ${counter.getSymbol}: ${this.balances.getBalance(exchange1, counter).get.getAvailable}")
    logger.info(s"Balance after trade. ${exchange2}: ${base.getSymbol}: ${this.balances.getBalance(exchange2, base).get.getAvailable}, ${counter.getSymbol}: ${this.balances.getBalance(exchange2, counter).get.getAvailable}")
  }

  private def logAfterExchangeStart(exchanging: Exchanging): Unit = {
    }

  private def logAfterExchangeEnd(exchanging: Exchanging): Unit = {
    val base = exchanging.trade.buyOrder.getCurrencyPair.base
    val counter = exchanging.trade.buyOrder.getCurrencyPair.counter

    val exchange1 = exchanging.trade.buyFrom
    val exchange2 = exchanging.trade.sellFrom

    logger.info(s"Balance after exchange. ${exchange1}: ${base.getSymbol}: ${this.balances.getBalance(exchange1, base).get.getAvailable}, ${counter.getSymbol}: ${this.balances.getBalance(exchange1, counter).get.getAvailable}")
    logger.info(s"Balance after exchange. ${exchange2}: ${base.getSymbol}: ${this.balances.getBalance(exchange2, base).get.getAvailable}, ${counter.getSymbol}: ${this.balances.getBalance(exchange2, counter).get.getAvailable}")
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
    val baseWithdrawalFee = withdrawalFeesStatic((trade.buyFrom, pair.base))
    val baseWithdrawalAmount = (baseAmount + baseWithdrawalFee) / 2

    val counterAmount = availableBalance(trade.sellFrom, pair.counter)
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
    BigDecimal(balances.getBalance(exchange, currency).map(_.getAvailable).getOrElse(BigDecimal(0).bigDecimal))

  private def updateBalancesForTrade(tradeDone: ArbitrageTrade): Unit = {
    val buyFee = tradeFeesPercent(tradeDone.buyFrom, tradeDone.arbitrage.currencyPair) * (tradeDone.buyAmount * tradeDone.buyOrder.getLimitPrice)
    balances.add(tradeDone.buyFrom, tradeDone.arbitrage.currencyPair.base, tradeDone.buyAmount )
    balances.subtract(tradeDone.buyFrom, tradeDone.arbitrage.currencyPair.counter, tradeDone.buyAmount * tradeDone.buyOrder.getLimitPrice + buyFee)

    val sellFee = tradeFeesPercent(tradeDone.sellFrom, tradeDone.arbitrage.currencyPair) * (tradeDone.sellAmount / tradeDone.sellOrder.getLimitPrice)
    balances.subtract(tradeDone.sellFrom, tradeDone.arbitrage.currencyPair.base, tradeDone.sellAmount)
    balances.add(tradeDone.sellFrom, tradeDone.arbitrage.currencyPair.counter, tradeDone.sellAmount * tradeDone.sellOrder.getLimitPrice - sellFee)
  }

  private def updateBalancesStartExchange(exchanging: Exchanging): Unit = {
    balances.subtract(exchanging.trade.buyFrom, exchanging.trade.arbitrage.currencyPair.base, exchanging.baseAmount)
    balances.subtract(exchanging.trade.sellFrom, exchanging.trade.arbitrage.currencyPair.counter, exchanging.counterAmount)
  }

  private def updateBalancesEndExchange(exchanging: Exchanging): Unit = {
    val baseFee = withdrawalFeesStatic((exchanging.trade.buyFrom, exchanging.trade.arbitrage.currencyPair.base))
    balances.add(exchanging.trade.sellFrom, exchanging.trade.arbitrage.currencyPair.base, exchanging.baseAmount - baseFee)

    val counterFee = withdrawalFeesStatic((exchanging.trade.sellFrom, exchanging.trade.arbitrage.currencyPair.counter))
    balances.add(exchanging.trade.buyFrom, exchanging.trade.arbitrage.currencyPair.counter, exchanging.counterAmount - counterFee)
  }

  private def getAccountService(exchange: Exchange): AccountService = exchange.getAccountService
  private def getTradeService(exchange: Exchange): TradeService = exchange.getTradeService
}
