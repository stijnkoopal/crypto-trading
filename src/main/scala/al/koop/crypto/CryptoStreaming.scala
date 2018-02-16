package al.koop.crypto

import org.knowm.xchange.Exchange
import org.knowm.xchange.currency.CurrencyPair
import org.knowm.xchange.dto.marketdata.Ticker
import org.slf4j.LoggerFactory
import rx.lang.scala.Observable

import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.duration._


class CryptoStreaming(val exchange: Exchange, currencyPair: CurrencyPair, pollInterval: Duration = 1 second) {
  private val logger = LoggerFactory.getLogger(getClass)

  private val cache: mutable.Map[(Exchange, CurrencyPair), Observable[(Ticker, Exchange)]] = mutable.Map()

  def ticker(): Observable[(Ticker, Exchange)] = {
    cache.getOrElseUpdate((exchange, currencyPair), Observable.interval(pollInterval)
      .map(_ => {
        try {
          Some(exchange.getMarketDataService.getTicker(currencyPair))
        } catch {
          case e: Exception =>
            logger.warn("Error during marketdata fetch, continuing", e)
            None
        }
      })
      .collect { case Some(x) => x }
      .map((_, exchange))
      .share)
  }
}
