package al.koop.crypto.strategy

import java.util

import org.knowm.xchange.currency.Currency
import org.knowm.xchange.dto.account.{AccountInfo, FundingRecord}
import org.knowm.xchange.service.account.AccountService
import org.knowm.xchange.service.trade.params.{TradeHistoryParams, WithdrawFundsParams}
import org.slf4j.LoggerFactory

class LoggingAccountService(private val realService: AccountService) extends AccountService {
  private val logger = LoggerFactory.getLogger(getClass)

  override def createFundingHistoryParams(): TradeHistoryParams = {
    logger.debug(s"createFundingHistoryParams() on $realService")
    realService.createFundingHistoryParams()
  }

  override def requestDepositAddress(currency: Currency, args: String*): String = {
    logger.debug(s"requestDepositAddress(${currency}, ${args}) on $realService")
    realService.requestDepositAddress(currency, args: _*)
  }

  override def getAccountInfo: AccountInfo = {
    logger.debug(s"getAccountInfo() on $realService")
    realService.getAccountInfo()
  }

  override def getFundingHistory(params: TradeHistoryParams): util.List[FundingRecord] = {
    logger.debug(s"getFundingHistory(${params}) on $realService")
    realService.getFundingHistory(params)
  }

  override def withdrawFunds(currency: Currency, amount: java.math.BigDecimal, address: String): String = {
    logger.debug(s"withdrawFunds(${currency}, ${amount}, ${address}) on $realService")
    realService.withdrawFunds(currency, amount, address)
  }

  override def withdrawFunds(params: WithdrawFundsParams): String = {
    logger.debug(s"withdrawFunds(${params}) on $realService")
    realService.withdrawFunds(params)
  }
}