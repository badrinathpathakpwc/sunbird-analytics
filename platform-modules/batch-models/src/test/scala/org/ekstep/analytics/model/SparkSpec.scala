package org.ekstep.analytics.model

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.ekstep.analytics.framework.Event
import org.ekstep.analytics.framework.JobContext
import org.ekstep.analytics.framework.conf.AppConf
import org.ekstep.analytics.framework.util.CommonUtil
import org.ekstep.analytics.framework.util.JSONUtils
import org.ekstep.analytics.framework.util.JobLogger
import org.apache.logging.log4j.Logger
import org.ekstep.analytics.framework.JobContext
import org.scalatest.BeforeAndAfterAll

import com.datastax.spark.connector.cql.CassandraConnector

/**
 * @author Santhosh
 */
class SparkSpec(val file: String = "src/test/resources/sample_telemetry.log") extends BaseSpec with BeforeAndAfterAll {

    var events: RDD[Event] = null;
    implicit var sc: SparkContext = null;

    override def beforeAll() {
    	super.beforeAll();
        JobLogger.init("org.ekstep.analytics.test-cases");
        sc = CommonUtil.getSparkContext(1, "TestAnalyticsCore");
        events = loadFile[Event](file);
    }

    override def afterAll() {
        JobContext.cleanUpRDDs();
        CommonUtil.closeSparkContext();
        super.afterAll()
    }

    def loadFile[T](file: String)(implicit mf: Manifest[T]): RDD[T] = {
        if (file == null) {
            return null;
        }
        sc.textFile(file, 1).filter { x => !x.isEmpty() }.map { line =>  
            {
                try {
            
                   JSONUtils.deserialize[T](line);
                } catch {
                    case ex: Exception =>
                        Console.err.println("Unable to parse line", line)
                        null.asInstanceOf[T]
                }
            }
        }.filter { x => x != null }.cache();
    }
}