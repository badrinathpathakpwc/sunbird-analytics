package org.ekstep.analytics.updater
import com.datastax.spark.connector._
import org.ekstep.analytics.framework.IBatchModel
import org.ekstep.analytics.framework._
import org.ekstep.analytics.framework.util.JSONUtils
import org.ekstep.analytics.util.Constants
import org.ekstep.analytics.framework.util.JobLogger
import org.apache.spark.{ SparkConf, SparkContext }
import org.apache.spark.rdd.RDD
import org.joda.time.DateTime
import com.pygmalios.reactiveinflux._
import com.pygmalios.reactiveinflux.spark._
import scala.concurrent.duration._
import java.net.URI
import com.typesafe.config.ConfigFactory
import org.ekstep.analytics.util.ContentUsageSummaryFact
import java.util.Calendar
import scala.util.matching.Regex
import org.ekstep.analytics.framework.util.CommonUtil
import scala.concurrent.{ Await, Future }
import org.ekstep.analytics.api.util.CommonUtil.getWeekRange
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import java.time.format.DateTimeFormatter
import org.ekstep.analytics.api.util.CommonUtil.dayPeriod
import org.ekstep.analytics.api.util.CommonUtil.monthPeriod
import org.ekstep.analytics.api.util.CommonUtil.weekPeriod
import org.ekstep.analytics.api.util.CommonUtil.weekPeriodLabel

case class Items(period: Int, template_id: String, number_of_Items: Double) extends Input with AlgoInput with AlgoOutput with Output
object UpdateInfluxDB extends IBatchModelTemplate[Items, Items, Items, Items] with Serializable {
    implicit val className = "org.ekstep.analytics.updater.UpdateInfluxDB"
    override def name: String = "UpdateInfluxDB"

    override def preProcess(data: RDD[Items], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[Items] = {
        val periods = org.ekstep.analytics.framework.util.CommonUtil.getPeriods(Constants.PERIOD_TYPE, Constants.PERIODS.toInt).toList
        val genieUsageSummaryFact = sc.cassandraTable[GenieUsageSummaryFact](Constants.CONTENT_KEY_SPACE_NAME, Constants.GENIE_LAUNCH_SUMMARY_FACT).where("d_period IN?", periods).collect().toList
        val contentUsageSummaryFact = sc.cassandraTable[ContentUsageSummaryFact](Constants.CONTENT_KEY_SPACE_NAME, Constants.CONTENT_USAGE_SUMMARY_FACT).where("d_period IN?", periods).collect().toList
        val genieRdd = sc.parallelize(genieUsageSummaryFact)
        val contentRdd = sc.parallelize(contentUsageSummaryFact)
        val filtereditems = data.filter { x => periods.contains(x.period) }
        import com.pygmalios.reactiveinflux.spark._
        /*create database if not created */
        checkdatabase
        /*converting data to line protocol......... */
        val genie = genieRdd.map { x => Point(time = new DateTime(periodTomilliSeconds(x.d_period.toString())), measurement = "genie_metrics", tags = Map("d_tag" -> x.d_tag, "week" -> x.d_period.toString()), fields = Map("m_total_sessions" -> x.m_total_sessions.toDouble, "m_total_ts" -> x.m_total_ts)) }
        val content = contentRdd.map { x => Point(time = new DateTime(periodTomilliSeconds(x.d_period.toString())), measurement = "content_metrics", tags = Map("d_tag" -> x.d_tag, "week" -> x.d_period.toString(), "d_content" -> x.d_content_id), fields = Map("m_total_sessions" -> x.m_total_sessions.toDouble, "m_total_ts" -> x.m_total_ts)) }
        val items = filtereditems.map { x => Point(time = new DateTime(periodTomilliSeconds(x.period.toString())), measurement = "item_metrics", tags = Map("template_id" -> x.template_id, "week" -> x.period.toString()), fields = Map("number_of_items" -> x.number_of_Items)) }
        implicit val params = ReactiveInfluxDbName(Constants.INFLUX_DB_NAME)
        implicit val awaitAtMost = 30.second
        val metrics = genie.union(content).union(items)
        metrics.saveToInflux()
        data
    }

    override def algorithm(data: RDD[Items], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[Items] = {
        data
    }

    override def postProcess(data: RDD[Items], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[Items] = {
        data
    }

    def periodTomilliSeconds(periods: String): Long = {
        var timestamp = 0l
        if (Constants.PERIOD_TYPE == "WEEK") {
            val week = periods.substring(0, 4) + "-" + periods.substring(5, periods.length);
            val firstDay = weekPeriodLabel.parseDateTime(week)
            val lastDay = firstDay.plusDays(7);
            timestamp = lastDay.getMillis
        }
        if (Constants.PERIOD_TYPE == "DAY") {
            timestamp = dayPeriod.parseDateTime(periods).getMillis
        }
        if (Constants.PERIOD_TYPE == "MONTH") {
            val days = monthPeriod.parseDateTime(periods).dayOfMonth().getMaximumValue
            timestamp = monthPeriod.parseDateTime(periods).plusDays(days).getMillis
        }
        timestamp
    }

    def checkdatabase = {
        import com.paulgoldbaum.influxdbclient._
        import scala.concurrent.ExecutionContext.Implicits.global
        val influxdb = InfluxDB.connect(Constants.LOCAL_HOST, Constants.INFLUX_DB_PORT.toInt)
        val database = influxdb.selectDatabase(Constants.INFLUX_DB_NAME)
        val res = Await.result(database.exists(), 10 seconds)
        if (res != true) {
            databaseCreation
        }
        def databaseCreation = {
            database.create()
            database.createRetentionPolicy(Constants.RETENTION_POLICY_NAME, Constants.RETENTION_POLICY_DURATION, Constants.RETENTION_POLICY_REPLICATION.toInt, true)
        }
    }

}