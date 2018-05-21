package al.koop.crypto.strategy

import org.knowm.xchange.{Exchange, ExchangeSpecification}
import org.knowm.xchange.currency.CurrencyPair
import org.knowm.xchange.dto.account.AccountInfo
import org.knowm.xchange.dto.marketdata.Ticker
import org.knowm.xchange.service.account.AccountService
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FlatSpec, Inside, Matchers}

import scala.collection.JavaConverters._

class SingleCurrencyArbitrageDetectorSpec extends FlatSpec with Matchers with MockitoSugar with Inside {

  val pair = CurrencyPair.BCH_ETH

  val accountServiceMock = mock[AccountService]
  when(accountServiceMock.getAccountInfo).thenReturn(new AccountInfo("", BigDecimal(0).bigDecimal))

  val specificationMock = mock[ExchangeSpecification]
  when(specificationMock.getExchangeName).thenReturn("")

  val exchange1Mock = mock[Exchange]
  when(exchange1Mock.getExchangeSymbols).thenReturn(List(pair).asJava)
  when(exchange1Mock.getAccountService).thenReturn(accountServiceMock)
  when(exchange1Mock.getExchangeSpecification).thenReturn(specificationMock)

  val exchange2Mock = mock[Exchange]
  when(exchange2Mock.getExchangeSymbols).thenReturn(List(pair).asJava)
  when(exchange2Mock.getAccountService).thenReturn(accountServiceMock)
  when(exchange2Mock.getExchangeSpecification).thenReturn(specificationMock)

  val arbitrageDetector = new SingleCurrencyArbitrageDetector(pair, exchange1Mock, exchange2Mock, 0.05)

  "isValidTrade" should "give no trade when bid < ask" in {
    val ticker1 = ticker(pair, bid = 100, ask = 120)
    val ticker2 = ticker(pair, bid = 90, ask = 110)

    val result = arbitrageDetector.isValidTrade((ticker1, exchange1Mock), (ticker2, exchange2Mock))

    result shouldBe None
  }

  "isValidTrade" should "give trade when bid > ask" in {
    val ticker1 = ticker(pair, bid = 130, ask = 131)
    val ticker2 = ticker(pair, bid = 90, ask = 100)

    val result = arbitrageDetector.isValidTrade((ticker1, exchange1Mock), (ticker2, exchange2Mock))

    inside(result) { case Some(DetectedArbitrage(_, _, buyPrice, buyFrom, sellPrice, sellFrom, diff)) => {
      buyPrice shouldBe 100
      sellPrice shouldBe 130
      buyFrom shouldBe exchange2Mock
      sellFrom shouldBe exchange1Mock
    }}
  }

  "isValidTrade" should "give trade when bid > ask, different exchange" in {
    val ticker1 = ticker(pair, bid = 90, ask = 100)
    val ticker2 = ticker(pair, bid = 130, ask = 131)

    val result = arbitrageDetector.isValidTrade((ticker1, exchange1Mock), (ticker2, exchange2Mock))

    inside(result) { case Some(DetectedArbitrage(_, _, buyPrice, buyFrom, sellPrice, sellFrom, diff)) => {
      buyPrice shouldBe 100
      sellPrice shouldBe 130
      buyFrom shouldBe exchange1Mock
      sellFrom shouldBe exchange2Mock
    }}
  }

  "isValidTrade" should "give no trade for too small difference" in {
    val ticker1 = ticker(pair, bid = 0.00017902	, ask = 0.00018010)
    val ticker2 = ticker(pair, bid = 0.00017851, ask = 0.00018278)

    val result = arbitrageDetector.isValidTrade((ticker1, exchange1Mock), (ticker2, exchange2Mock))

    result shouldBe None
  }

  "isValidTrade" should "give no trade for too small difference 2" in {
    val ticker1 = ticker(pair, bid = 0.00058804	, ask = 0.00058988)
    val ticker2 = ticker(pair, bid = 0.00058637, ask = 0.00059539)

    val result = arbitrageDetector.isValidTrade((ticker1, exchange1Mock), (ticker2, exchange2Mock))

    result shouldBe None
  }

  "isValidTrade" should "give no trade for too small difference 3" in {
    val ticker1 = ticker(pair, bid = 0.02915000	, ask = 0.02916400)
    val ticker2 = ticker(pair, bid = 0.02933741, ask = 0.02933742)

    val result = arbitrageDetector.isValidTrade((ticker1, exchange1Mock), (ticker2, exchange2Mock))

    result shouldBe None
  }

  private def ticker(currencyPair: CurrencyPair, bid: BigDecimal, ask: BigDecimal) =
    new Ticker.Builder()
      .ask(ask.bigDecimal)
      .bid(bid.bigDecimal)
      .currencyPair(currencyPair)
      .build()

}
