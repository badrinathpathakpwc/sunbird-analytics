package org.ekstep.analytics.adhoc

import org.ekstep.analytics.model.SparkSpec
import org.ekstep.analytics.framework.V3Event
import org.ekstep.analytics.framework.DataFilter
import org.ekstep.analytics.framework.Filter
import org.ekstep.analytics.framework.util.JSONUtils
import org.ekstep.analytics.framework.util.CommonUtil
import org.ekstep.analytics.framework.V3Context

import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import java.io.File
import java.net.URL
import org.ekstep.analytics.framework.OutputDispatcher
import org.ekstep.analytics.framework.Dispatcher
import org.ekstep.analytics.model.WorkFlowSummaryModel

class InvalidEvent(val eid: String, val ets: Long, val mid: String, val context: V3Context) extends Serializable

class TestAdhocScripts extends SparkSpec(null) {

    //"TestAdhocScripts" should "execute the script to compute usage details" in {
    ignore should "execute the script to compute usage details" in {
        val rdd = loadFile[V3Event]("/Users/santhosh/ekStep/telemetry/raw/2018-06-02/2018-06-02*").cache();

        val appEvents = rdd.filter(f => f.context.pdata.get.id.equals("prod.diksha.app")).cache();

        val contentDownloaded = DataFilter.filter(appEvents, Array(
            Filter("eid", "EQ", Option("SHARE")),
            Filter("edata.type", "EQ", Option("CONTENT")),
            Filter("edata.dir", "EQ", Option("In")))).cache();

        val contentDownloadedCount = contentDownloaded.count();
        val mostDownloadedContent = contentDownloaded.map(f => f.edata.items).flatMap(f => f.map(f => f)).groupBy(f => f.id).mapValues(f => f.size).sortBy(f => f._2, false, 1).collect().head;
        contentDownloaded.unpersist(true);

        val mostViewedContent = appEvents.filter(f => f.eid.equals("START") && f.context.pdata.get.pid.get.equals("sunbird.app.contentplayer")).map(f => f.`object`.get).groupBy(f => f.id).mapValues(f => f.size).sortBy(f => f._2, false, 1).collect().head;
        val distinctAppUsers = appEvents.map(f => f.actor.id).distinct().count();
        val dialScans = appEvents.filter(f => f.eid.equals("START") && f.edata.`type`.equals("qr")).count;

        appEvents.unpersist(true);

        val portalEvents = rdd.filter(f => f.context.pdata.get.id.equals("prod.diksha.portal")).cache();
        val distinctPortalUsers = portalEvents.map(f => f.actor.id).distinct().count();

        val rdd2 = loadFile[V3Event]("/Users/santhosh/ekStep/telemetry/raw/2018-06-02/1970-01-01*").cache();
        val contentCreated = rdd2.filter(f => f.eid.equals("AUDIT") && "Draft".equals(f.edata.state) && !f.context.channel.equals("in.ekstep")).count();
        val totalDialScans = rdd2.filter(f => f.eid.equals("SEARCH")).map(f => f.edata.filters.get.asInstanceOf[Map[String, AnyRef]]).filter(f => f.contains("dialcodes")).count();
        val dialCodeLinked = rdd2.filter(f => f.eid.equals("AUDIT") && f.edata.props.contains("dialcodes")).count;

        Console.println("Total Contents Downloaded:", contentDownloadedCount);
        Console.println("Content Most Downloaded:", mostDownloadedContent);
        Console.println("Content Most Viewed:", mostViewedContent);
        Console.println("Distinct App Users:", distinctAppUsers);
        Console.println("In APP Dial Scans:", dialScans);
        Console.println("Distinct Portal Users:", distinctPortalUsers);
        Console.println("Content Created:", contentCreated);
        Console.println("Total Dial Scans:", totalDialScans);
        Console.println("Dial Codes Linked:", dialCodeLinked);
    }

    ignore should "fetch the API stats" in {

        val apisToTrack = List("/learning-service/v3/framework/read", "/learning-service/v3/content/hierarchy", "/learning-service/v3/content/read", "/v3/search", "/v2/search", "");
        val rdd = loadFile[V3Event]("/Users/santhosh/ekStep/telemetry/raw/2018-06-02/1970-01-01*").cache();
        val apiLogs = rdd.filter(f => f.eid.equals("LOG") && f.edata.`type`.equals("api_access")).map(f => {
            var rid: String = null;
            var url: String = null;
            var duration: Int = 0;
            var status: Int = 0;
            f.edata.params.foreach(f => {
                if (f.contains("url")) {
                    url = f.get("url").get.asInstanceOf[String]
                }
                if (f.contains("rid")) {
                    rid = f.get("rid").get.asInstanceOf[String]
                }
                if (f.contains("duration")) {
                    duration = f.get("duration").get.asInstanceOf[Double].toInt
                }
                if (f.contains("status")) {
                    status = f.get("status").get.asInstanceOf[Double].toInt
                }
            })
            if(rid == null) {
                rid = url;
            }
            (rid, duration, status)
        }).groupBy(f => f._1).mapValues(f => {
            val responseTimes = f.map(f => f._2).toList;
            (f.head._1, f.size, CommonUtil.avg(responseTimes), responseTimes.min, responseTimes.max)
        }).sortBy(f => f._2._2, false, 10).collect();

        Console.println("");
        apiLogs.foreach(f => println(f._2));
        
    }
    
    ignore should "get failed counts by pdata.id" in {
        
        val rdd = loadFile[String]("/Users/santhosh/ekStep/telemetry/invalid/prod.diksha.portal.invalid.log");
        val rdd1 = rdd.filter(f => TelemetryValidator.validate(f));
        rdd1.saveAsTextFile("output/prod.diksha.portal.valid.log");
        //Console.println("rdd1 count", rdd1.count());
    }
    
    it should "validate the events" in {
        
        //val event = """{"actor":{"id":"96d1f6be-a79e-430f-b136-23c22b818911","type":"User"},"context":{"cdata":[{"id":"b71dc82cfb72b422878ad7ecea6ebc41","type":"ContentSession"}],"channel":"0123221758376673287017","did":"e60b1eef101b8c373cbb2caab688d99b","env":"edit","pdata":{"id":"prod.diksha.portal","pid":"sunbird-portal.contenteditor.contentplayer","ver":"1.0"},"rollup":{},"sid":"TUtLpksYbwA3LamJd8ypBBI6vvixO3hI"},"edata":{"target":{"id":"org.ekstep.questionunit.ftb","type":"plugin","ver":"1.0"},"values":[]},"eid":"RESPONSE","ets":1.527067794119E12,"mid":"RESPONSE:eb661c89bf6b1fa0b8769312202d0556","object":{"id":"do_31248414188251545612869","type":"Content","ver":"1.0"},"tags":[],"ver":"3.0","@version":"1","@timestamp":"2018-05-23T09:31:13.732Z","metadata":{"checksum":"RESPONSE:eb661c89bf6b1fa0b8769312202d0556"},"uuid":"4","key":"4","type":"events","ts":"2018-05-23T09:29:54.119+0000"}""";
        //TelemetryValidator.validate(event);
    }
    
    ignore should "run the WFS" in {
        
        val rdd = loadFile[V3Event]("/Users/santhosh/ekStep/telemetry/raw/2018-06-04*");
        val wfsEvents = WorkFlowSummaryModel.execute(rdd, None);
        Console.println("WFS events count:", wfsEvents.count());
    }

}

object TelemetryValidator {
    
    val jsonSchemaFactory = JsonSchemaFactory.byDefault();
    case class EventID(eid: String);
    
    def validate(event: String) : Boolean = {
        val eventId = JSONUtils.deserialize[EventID](event);
        val url = new URL("http://localhost:7070/schemas/3.0/" + eventId.eid.toLowerCase() + ".json");
        val schemaJson = JsonLoader.fromURL(url);
        val eventJson = JsonLoader.fromString(event);
        
        val jsonSchema = jsonSchemaFactory.getJsonSchema(schemaJson);
        val report = jsonSchema.validate(eventJson);
        //Console.println("Report", report.toString());
        report.isSuccess();
    }
    
}
