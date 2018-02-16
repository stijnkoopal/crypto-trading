package al.koop.crypto.strategy

import java.util

import org.knowm.xchange.dto.Order
import org.knowm.xchange.dto.trade.{LimitOrder, MarketOrder, OpenOrders, UserTrades}
import org.knowm.xchange.service.trade.TradeService
import org.knowm.xchange.service.trade.params.{CancelOrderParams, TradeHistoryParams}
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams
import org.slf4j.LoggerFactory

class LoggingTradeService(private val realService: TradeService) extends TradeService {
  private val logger = LoggerFactory.getLogger(getClass)

  override def getTradeHistory(params: TradeHistoryParams): UserTrades = {
    logger.debug(s"getTradeHistory(${params}) on $realService")
    realService.getTradeHistory(params)
  }

  override def cancelOrder(orderId: String): Boolean = {
    logger.debug(s"cancelOrder(${orderId}) on $realService")
    realService.cancelOrder(orderId)
  }

  override def cancelOrder(orderParams: CancelOrderParams): Boolean = {
    logger.debug(s"cancelOrder(${orderParams}) on $realService")
    realService.cancelOrder(orderParams)
  }

  override def verifyOrder(limitOrder: LimitOrder): Unit = {
    logger.debug(s"verifyOrder(${limitOrder}) on $realService")
    realService.verifyOrder(limitOrder)
  }

  override def verifyOrder(marketOrder: MarketOrder): Unit = {
    logger.debug(s"verifyOrder(${marketOrder}) on $realService")
    realService.verifyOrder(marketOrder)
  }

  override def placeLimitOrder(limitOrder: LimitOrder): String = {
    logger.debug(s"placeLimitOrder(${limitOrder}) on $realService")
    realService.placeLimitOrder(limitOrder)
  }

  override def createTradeHistoryParams(): TradeHistoryParams = {
    logger.debug(s"createTradeHistoryParams() on $realService")
    realService.createTradeHistoryParams()
  }

  override def getOpenOrders: OpenOrders = {
    logger.debug(s"getOpenOrders() on $realService")
    realService.getOpenOrders()
  }

  override def getOpenOrders(params: OpenOrdersParams): OpenOrders = {
    logger.debug(s"getOpenOrders(${params}) on $realService")
    realService.getOpenOrders(params)
  }

  override def createOpenOrdersParams(): OpenOrdersParams = {
    logger.debug(s"createOpenOrdersParams() on $realService")
    realService.createOpenOrdersParams()
  }

  override def getOrder(orderIds: String*): util.Collection[Order] = {
    logger.debug(s"getOrder(${orderIds}) on $realService")
    realService.getOrder()
  }

  override def placeMarketOrder(marketOrder: MarketOrder): String = {
    logger.debug(s"placeMarketOrder(${marketOrder}) on $realService")
    realService.placeMarketOrder(marketOrder)
  }
}
