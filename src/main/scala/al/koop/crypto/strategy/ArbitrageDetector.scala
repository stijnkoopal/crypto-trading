package al.koop.crypto.strategy

import java.time.LocalDateTime
import java.time.LocalDateTime.now

import al.koop.crypto.CryptoStreaming
import org.knowm.xchange.Exchange
import org.knowm.xchange.currency.{Currency, CurrencyPair}
import org.knowm.xchange.dto.marketdata.Ticker
import org.slf4j.LoggerFactory
import rx.lang.scala.Observable

import scala.collection.JavaConverters._
import scala.concurrent.duration._

case class DetectedArbitrage(dateTime: LocalDateTime,
                             currencyPair: CurrencyPair,
                             ask: BigDecimal,
                             buyFrom: Exchange,
                             bid: BigDecimal,
                             sellFrom: Exchange,
                             difference: BigDecimal)

trait ArbitrageDetector {
  def detect(): Observable[DetectedArbitrage]

  def priceDiff(price1: BigDecimal, price2: BigDecimal): BigDecimal = {
    (price1 - price2) / price2
  }
}

class SingleCurrencyArbitrageDetector(val currencyPair: CurrencyPair,
                                      val exchange1: Exchange,
                                      val exchange2: Exchange,
                                      val diff: BigDecimal = 0.05,
                                      val pollInterval: Duration = 1 second) extends ArbitrageDetector {
  private val logger = LoggerFactory.getLogger(getClass)

  if (!exchange1.getExchangeSymbols.contains(currencyPair))
    throw new Exception(s"First exchange has not listed $currencyPair")

  if (!exchange2.getExchangeSymbols.contains(currencyPair))
    throw new Exception(s"Second exchange has not listed $currencyPair")

  override def detect(): Observable[DetectedArbitrage] = {
    logger.info(s"Starting arbitrage detection on ${exchange1.getExchangeSpecification.getExchangeName} and ${exchange2.getExchangeSpecification.getExchangeName} for $currencyPair")

    val ticker1 = new CryptoStreaming(exchange1, currencyPair, pollInterval)
      .ticker()

    val ticker2 = new CryptoStreaming(exchange2, currencyPair, pollInterval)
      .ticker()

    ticker1.combineLatest(ticker2)
      .map(e => isValidTrade(e._1, e._2))
      .collect { case Some(x) => x }
  }

  def isValidTrade(t1: (Ticker, Exchange), t2: (Ticker, Exchange)): Option[DetectedArbitrage] = {
    val pair = t1._1.getCurrencyPair

    val priceDiff1 = priceDiff(t1._1.getBid, t2._1.getAsk)
    val priceDiff2 = priceDiff(t2._1.getBid, t1._1.getAsk)

    logger.debug(s"Checking pair $pair, ask=${t1._1.getAsk}/bid=${t1._1.getBid} on ${t1._2.getExchangeSpecification.getExchangeName}; ask=${t2._1.getAsk}/bid=${t2._1.getBid} on ${t2._2.getExchangeSpecification.getExchangeName}. Differences: $priceDiff1 and $priceDiff2")

    if (priceDiff1 >= diff)
      Some(DetectedArbitrage(now(), pair, ask = t2._1.getAsk, buyFrom = t2._2, bid = t1._1.getBid, sellFrom = t1._2, priceDiff1))

    else if (priceDiff2 >= diff)
      Some(DetectedArbitrage(now(), pair, ask = t1._1.getAsk, buyFrom = t1._2, bid = t2._1.getBid, sellFrom = t2._2, priceDiff2))

    else
      None
  }
}

class MultiCurrencyArbitrageDetector(val exchange1: Exchange,
                                     val exchange2: Exchange,
                                     val pairs: Set[CurrencyPair],
                                     val diff: BigDecimal = 0.05) extends ArbitrageDetector {
  private val set1 = exchange1.getExchangeSymbols.asScala.to[Set]
  private val set2 = exchange2.getExchangeSymbols.asScala.to[Set]

  val pairDifference: Set[CurrencyPair] = pairs.diff(set1.intersect(set2))
  if (pairDifference.nonEmpty)
    throw new Exception(s"Not all pairs supported: $pairDifference are not")

  override def detect(): Observable[DetectedArbitrage] = {
    pairs.map(new SingleCurrencyArbitrageDetector(_, exchange1, exchange2, diff, pollInterval = 5 seconds))
      .map(_.detect())
      .foldLeft(Observable.just[DetectedArbitrage]()) { case (acc, value) => acc.merge(value) }
  }
}

class AllCurrencyArbitrageDetector(val counter: Currency,
                                   val exchange1: Exchange,
                                   val exchange2: Exchange,
                                   val diff: BigDecimal = 0.05) extends ArbitrageDetector {
  private val set1 = exchange1.getExchangeSymbols.asScala.to[Set]
  private val set2 = exchange2.getExchangeSymbols.asScala.to[Set]
  private val pairs = set1.intersect(set2)
    .filter(_.counter == counter)

  override def detect(): Observable[DetectedArbitrage] = {
    new MultiCurrencyArbitrageDetector(exchange1, exchange2, pairs, diff)
      .detect()
  }
}

class AllExchangesArbitrageDetector(val counter: Currency,
                                    val diff: BigDecimal,
                                    val exchange: Exchange*) extends ArbitrageDetector {

  if (exchange.isEmpty) throw new Exception("At least one exchange expected")

  private val combinations = exchange.combinations(2)
    .map(seq => (seq.head, seq(1)))
    .filter { case (a, b) => a != b }

  override def detect(): Observable[DetectedArbitrage] = {
    combinations.map { case (a, b) => new AllCurrencyArbitrageDetector(counter, a, b, diff)}
      .map(_.detect())
      .foldLeft(Observable.just[DetectedArbitrage]()) { case (acc, value) => acc.merge(value) }
  }
}