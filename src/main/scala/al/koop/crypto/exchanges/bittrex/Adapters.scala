package al.koop.crypto.exchanges.bittrex

import java.time.{LocalDateTime, ZoneId}

import al.koop.crypto.PriceChangeEvent
import org.knowm.xchange.dto.marketdata.Ticker
import rx.lang.scala.Observable

object Adapters {
  implicit def ticker2PriceChangeEvent(ticker: Ticker): PriceChangeEvent =
    PriceChangeEvent(LocalDateTime.ofInstant(ticker.getTimestamp.toInstant, ZoneId.systemDefault()), BigDecimal(ticker.getAsk), BigDecimal(ticker.getBid))
  implicit def ticker2PriceChangeEvent(tickers: Observable[Ticker]): Observable[PriceChangeEvent] = tickers.map(ticker2PriceChangeEvent)
}
