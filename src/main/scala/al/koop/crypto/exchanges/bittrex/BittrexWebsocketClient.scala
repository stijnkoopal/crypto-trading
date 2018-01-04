package al.koop.crypto.exchanges.bittrex

import rx.lang.scala.Observable
import scala.concurrent.duration._

class BittrexWebsocketClient private (private val currencyPair: String) {
  def ticker() = ???
}
