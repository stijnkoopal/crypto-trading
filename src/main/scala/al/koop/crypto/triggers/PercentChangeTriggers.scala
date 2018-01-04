package al.koop.crypto.triggers

import al.koop.crypto.Crypto.Percentage
import al.koop.crypto.PriceChangeEvent
import rx.lang.scala.Observable

import scala.concurrent.duration._

class PercentChangeTrigger(val timeSpan: Duration, percentChange: Percentage)  {
  private val openings: Observable[Long] = Observable.interval(0 seconds, 2 seconds)
  private val closings = (_: Long) => Observable.timer(timeSpan)

  def apply(priceChanges: Observable[PriceChangeEvent]): Observable[PriceChangeEvent] =
    priceChanges.slidingBuffer(openings)(closings)
      .map(window => (window.head, window.last))
      .filter { case (start, end) => calculateChange(start, end) > percentChange}
      .map { case (_, end) => end }

  private def calculateChange(start: PriceChangeEvent, end: PriceChangeEvent) = (end.price / start.price) - 1
}
