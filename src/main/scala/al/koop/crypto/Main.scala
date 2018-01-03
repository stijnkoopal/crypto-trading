package al.koop.crypto

import al.koop.crypto.binance.BinanceWebsocketClient
import al.koop.crypto.binance.BinanceWebsocketEvents.TradeEvent
import org.slf4j.LoggerFactory
import rx.lang.scala.Observable

import scala.concurrent.duration._

object Main {
  def main(args: Array[String]): Unit = {
    val client = BinanceWebsocketClient("sntbtc")
    client.trades()

    PercentChangeTrigger(timeSpan = 20 seconds, percentChange = 0.1)(client.trades())
      .subscribe(println(_))
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

object PercentChangeTrigger {
  def apply(timeSpan: Duration, percentChange: Double) =
    new PercentChangeTrigger(timeSpan, percentChange)(_)
}
