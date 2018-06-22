package ai.jh

import java.io.OutputStreamWriter
import java.math.BigDecimal
import java.net.{HttpURLConnection, URL}
import java.time.{Instant, LocalDateTime, ZoneId}

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import org.knowm.xchange.{Exchange, ExchangeFactory, ExchangeSpecification}
import org.knowm.xchange.bitfinex.v1.{BitfinexExchange, BitfinexOrderType}
import org.knowm.xchange.bitfinex.v1.dto.account.BitfinexBalancesResponse
import org.knowm.xchange.bitfinex.v1.dto.marketdata.{BitfinexLendDepth, BitfinexLendLevel}
import org.knowm.xchange.bitfinex.v1.service.{BitfinexAccountServiceRaw, BitfinexMarketDataServiceRaw, BitfinexTradeServiceRaw}
import org.knowm.xchange.dto.Order.OrderType
import org.knowm.xchange.dto.trade.FixedRateLoanOrder

object BotConf {

  val conf = ConfigFactory.load()
  val lineuser: String = conf.getString("jh.line.userkey")
  val apiKey: String = conf.getString("jh.bitfinex.apikey")
  val secretKey: String = conf.getString("jh.bitfinex.secretkey")
  val lineauth = conf.getString("jh.line.authorization")

}
object Bot {
  val log = Logger(getClass)
  val bfx: Exchange = ExchangeFactory.INSTANCE.createExchange(classOf[BitfinexExchange].getName)

  val bfxSpec: ExchangeSpecification = bfx.getDefaultExchangeSpecification

  val lineuser = BotConf.lineuser
  val lineauth = BotConf.lineauth
  bfxSpec.setApiKey(BotConf.apiKey)
  bfxSpec.setSecretKey(BotConf.secretKey)

  bfx.applySpecification(bfxSpec)

  // Get the necessary services
  val marketDataService: BitfinexMarketDataServiceRaw = bfx.getMarketDataService.asInstanceOf[BitfinexMarketDataServiceRaw]
  val accountService: BitfinexAccountServiceRaw = bfx.getAccountService.asInstanceOf[BitfinexAccountServiceRaw]
  val tradeService: BitfinexTradeServiceRaw = bfx.getTradeService.asInstanceOf[BitfinexTradeServiceRaw]


  val targetMin = Map("usd" -> math.BigDecimal(51D),"btc" -> math.BigDecimal(0.05D), "eth" -> math.BigDecimal(0.20D),  "bch" -> math.BigDecimal(0.05D), "ltc" -> math.BigDecimal(0.5D),"eos" -> math.BigDecimal(3D), "eos" -> math.BigDecimal(5D),"xrp" -> math.BigDecimal(120D))

  def getStats = {
    targetMin.map(x=>{

    })

  }

  def time(t:java.math.BigDecimal) = {
    LocalDateTime.ofInstant(Instant.ofEpochMilli(t.longValue()*1000), ZoneId.systemDefault())
  }
  def main(args: Array[String]): Unit = {
    while (true) {
      try {
        val balances: Array[BitfinexBalancesResponse] = accountService.getBitfinexAccountInfo
        val activeOffers = tradeService.getBitfinexOpenOffers
        val activeCredits = tradeService.getBitfinexActiveCredits


        val msg = activeOffers.filter(x => (System.currentTimeMillis().doubleValue() / 1000D - x.getTimestamp.doubleValue()) / 60D > 3).map(o => {
          log.info(s"OFFERED,cur=${o.getCurrency},time=${time(o.getTimestamp)},amount=${o.getRemainingAmount}")
          log.info(s"retry old one(10m),cur=${o.getCurrency},amount=${o.getRemainingAmount},time=${time(o.getTimestamp)}")
          val cancel = tradeService.cancelBitfinexOffer(o.getId.toString)
          log.info(s"cancel offer, ${cancel.toString}")

          val rate = o.getRate.doubleValue() * 0.98
          val order = if((rate/365D)>0.1) {
            new FixedRateLoanOrder(OrderType.ASK, o.getCurrency, o.getRemainingAmount, 30, "", null, new BigDecimal(rate))
          }else if((rate/365D)<0.0035D) {
            new FixedRateLoanOrder(OrderType.ASK, o.getCurrency, o.getRemainingAmount, 2, "", null, new BigDecimal(0.0035D * 365D))
          }else{
            new FixedRateLoanOrder(OrderType.ASK, o.getCurrency, o.getRemainingAmount, 2, "", null, new BigDecimal(rate))
          }

          if((rate/365D)>0.0035D) {
            val place = tradeService.placeBitfinexFixedRateLoanOrder(order, BitfinexOrderType.LIMIT)
            log.info(s"OLD REDUCED!!,place=${place},order=${order}")
            f"${place.getCurrency.take(1)}%s , ${place.getRemainingAmount.doubleValue()}%1.2f   ,   ${(rate/365D)}%1.4f"
          }else{
            log.info(s"TOO LOW RATE!!,order=${order}")
            f"TLR ${o.getCurrency.take(1)}, ${o.getRemainingAmount.doubleValue}   ,   ${(rate/365D)}%1.4f"
          }





        })

        val msg2 = balances.filter(x => x.getType == "deposit").map(b => {
          if (math.BigDecimal(b.getAvailable.doubleValue()) > targetMin.getOrElse(b.getCurrency.toLowerCase(), Double.MaxValue)) {
            log.info(s"ENOUGH, ${b.getCurrency} ${b.getAvailable}")

            val lends: BitfinexLendDepth = marketDataService.getBitfinexLendBook(b.getCurrency.toLowerCase, 0, 200)

            val calRates = lends.getAsks.map(x => {
              ((x.getRate.doubleValue() * 100000D).toInt, x)
            }).groupBy(x => {
              x._1
            }).map(x =>
              (new BitfinexLendLevel(
                new BigDecimal(x._1.toDouble / 100000D),
                new BigDecimal(x._2.map(y => y._2.getAmount.doubleValue()).sum),
                2,
                0, ""))
            ).toSeq

            val slidingsdata = calRates.sortBy(x => x.getRate.doubleValue() * -1).sliding(2, 1).toList
              val (left, right, amo) = if(slidingsdata.size>1 && slidingsdata.head.size >1) {
                val a = slidingsdata.map(x => {
                  //log.info(s"${x(0).getRate.doubleValue() / 365D}, ${x(0).getAmount.doubleValue()}")
                  ((x(0).getRate.doubleValue(), x(1).getRate.doubleValue()), x(1).getAmount.doubleValue() - x(0).getAmount.doubleValue())
                }).maxBy(x => x._2)
                (a._1._1, a._1._2, a._2)
              } else (0D, 0D, 0D)


            val min = calRates.minBy(x=>x.getRate.doubleValue()).getRate.doubleValue()

            val frr = lends.getAsks.filter(x=>{x.getFrr.toLowerCase == "yes"}).headOption.map(x=>x.getRate.doubleValue()).getOrElse(lends.getAsks.maxBy(x=>x.getRate.doubleValue()).getRate.doubleValue())
            val avg = (((left + right+0.0000001) / 2D) * 0.5D + frr * 0.5D)
            val rate = if((min +  min*0.3) < avg){
              min + min*0.3
            } else if(avg < min) (min +  min*0.15) else avg

            log.info(s"MAX,cur=${b.getCurrency},slide=${min},frr=${frr},left=${right / 365D},right${left/365D} avg=${rate / 365}")


            val order = if((rate/365D)>0.1) {
              new FixedRateLoanOrder(OrderType.ASK, b.getCurrency, b.getAvailable, 30, "", null, new BigDecimal(rate))
            }else if((rate/365D)<0.0035D) {
              new FixedRateLoanOrder(OrderType.ASK, b.getCurrency, b.getAvailable, 2, "", null, new BigDecimal(0.0035D * 365D))
            }else{
              new FixedRateLoanOrder(OrderType.ASK, b.getCurrency, b.getAvailable, 2, "", null, new BigDecimal(rate))
            }

            log.info(s"ORDER ${order}")
            val place = tradeService.placeBitfinexFixedRateLoanOrder(order, BitfinexOrderType.LIMIT)
            log.info(s"PLACE ${place} ${order}")

            f"${place.getCurrency.take(1)}%s , ${place.getRemainingAmount.doubleValue()}%1.2f   ,   ${(rate/365D)}%1.4f"
          } else {
            log.info(s"SMALL, ${b.getCurrency}, ${b.getAvailable}")
            f"SM ${b.getCurrency.take(1)}%s, ${b.getAvailable}%1.2f"
          }

        })

        if(msg.size>0 && msg2.filter(x=>x.startsWith("SM")).size>0) {
          val totalMsg = s"${msg.mkString("\\n")}\\n${msg2.filter(x => x.startsWith("SM")).mkString("\\n")}"

          val conn = new URL("https://api.line.me/v2/bot/message/push").openConnection().asInstanceOf[HttpURLConnection]
          conn.setRequestProperty("Content-Type", "application/json")
          conn.setRequestProperty("Authorization", lineauth)
          conn.setRequestMethod("POST")
          conn.setDoOutput(true)
          val os = new OutputStreamWriter(conn.getOutputStream, "UTF8")
          val msgl = f"""{"to": "$lineuser%s","messages":[{"type":"text","text": "$totalMsg"}]}"""
          log.info(msgl)
          os.write(msgl)
          os.close
          if (conn.getResponseCode() != 200) {
            log.info(s"LINESEND ${conn.getResponseCode}, ${scala.io.Source.fromInputStream(conn.getErrorStream, "UTF8").mkString} $msg")
          }
        }
        Thread.sleep(60000)
      }catch{
        case e:Exception => e.printStackTrace()
      }

    }
  }
}
