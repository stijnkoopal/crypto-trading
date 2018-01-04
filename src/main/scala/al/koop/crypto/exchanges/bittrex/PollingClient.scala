package al.koop.crypto.exchanges.bittrex

import org.knowm.xchange.Exchange
import org.knowm.xchange.currency.CurrencyPair
import org.knowm.xchange.dto.marketdata.Ticker
import rx.lang.scala.Observable
import rx.lang.scala.schedulers.IOScheduler

import scala.concurrent.duration._

class PollingClient private(val currencyPair: CurrencyPair, val exchange: Exchange, val pollInterval: Duration) {
  def ticker(): Observable[Ticker] =
    Observable.interval(pollInterval, IOScheduler())
      .map(_ => exchange.getMarketDataService.getTicker(currencyPair))
}

object PollingClient {
  def apply(currencyPair: CurrencyPair, pollInterval: Duration)(exchange: Exchange) = new PollingClient(currencyPair, exchange, pollInterval)
}
