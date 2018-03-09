package org.ekstep.analytics.model

import org.ekstep.analytics.framework.V3Event
import scala.collection.mutable.Buffer
import org.ekstep.analytics.framework.V3PData
import org.ekstep.analytics.framework.util.JSONUtils
import org.ekstep.analytics.framework.OutputDispatcher
import org.ekstep.analytics.framework._

case class WorkflowDataRead(did: Option[String], sid: String, uid: String, pdata: PData, channel: String, content_id: Option[String], session_type: String, syncts: Long, dt_range: DtRange, mode: Option[String], item_responses: Option[Buffer[ItemResponse]],
                          start_time: Long, end_time: Long, time_spent: Double, time_diff: Double, interact_events_count: Long, interact_events_per_min: Double, telemetry_version: String,
                          env_summary: Option[Iterable[EnvSummary]], events_summary: Option[Iterable[EventSummary]],
                          page_summary: Option[Iterable[PageSummary]], etags: Option[ETags])

class TestWorkFlowSummaryModel extends SparkSpec {

    it should "generate 9 workflow summary" in {
        val data = loadFile[V3Event]("src/test/resources/workflow-summary/test-data1.log")
        val out = WorkFlowSummaryModel.execute(data, None)
        out.count() should be(9)
        val me = out.collect();

        val appSummaryEvent1 = me.filter { x => x.dimensions.`type`.get.equals("app") }
        val sessionSummaryEvent1 = me.filter { x => x.dimensions.`type`.get.equals("session") }
        val playerSummaryEvent1 = me.filter { x => x.dimensions.`type`.get.equals("player") }
        val editorSummaryEvent1 = me.filter { x => x.dimensions.`type`.get.equals("editor") }

        appSummaryEvent1.size should be(1)
        sessionSummaryEvent1.size should be(1)
        playerSummaryEvent1.size should be(7)
        editorSummaryEvent1.size should be(0)

        val event1 = playerSummaryEvent1.filter(f => f.mid.equals("C61540B8D74BEA21087A2DD3BDC960A3")).last

        // Validate for item response, env summary & page summary
        event1.eid should be("ME_WORKFLOW_SUMMARY");
        event1.context.pdata.model.get should be("WorkflowSummarizer");
        event1.context.pdata.ver should be("1.0");
        event1.context.granularity should be("SESSION");
        event1.context.date_range should not be null;
        event1.dimensions.`type`.get should be("player");
        event1.dimensions.content_id.get should be("do_20093384");
        event1.dimensions.did.get should be("11573c50cae2078e847f12c91a2d1965eaa73714");
        event1.dimensions.sid.get should be("830811c2-3c02-4c45-8755-3f15064a88a2");
        event1.dimensions.mode.get should be("play")

        val summary1 = JSONUtils.deserialize[WorkflowDataRead](JSONUtils.serialize(event1.edata.eks));
        summary1.interact_events_per_min should be(19);
        summary1.start_time should be(1515404389226L);
        summary1.interact_events_count should be(19);
        summary1.end_time should be(1515404399264L);
        summary1.time_diff should be(10.04);
        summary1.time_spent should be(10.05);
        summary1.page_summary.get.size should be(5);
        summary1.env_summary.get.size should be(1);
        summary1.events_summary.get.size should be(5);
        summary1.telemetry_version should be("3.0");
        
        val esList1 = summary1.events_summary.get
        esList1.size should be(5);
        val esMap1 = esList1.map { x => (x.id, x.count) }.toMap
        esMap1.get("INTERACT").get should be(19);
        esMap1.get("START").get should be(1);
        esMap1.get("IMPRESSION").get should be(5);
        esMap1.get("END").get should be(1);
        esMap1.get("ASSESS").get should be(4);

        val pageSummary1 = summary1.page_summary.get.head
        pageSummary1.env should be("ContentPlayer")
        pageSummary1.id should be("scene040ff05f-97f6-4041-ad35-a116cbbb795f")
        pageSummary1.`type` should be("workflow")
        pageSummary1.time_spent should be(2.09)
        pageSummary1.visit_count should be(1)
        
        val envSummary1 = summary1.env_summary.get.head
        envSummary1.env should be("ContentPlayer")
        envSummary1.time_spent should be(4.6)
        envSummary1.count should be(1)
        
        val itemResponses = summary1.item_responses.get
        itemResponses.size should be(4)
        
        val itemRes1 = itemResponses.head
        itemRes1.itemId should be("do_20093383")
        itemRes1.timeSpent.get should be(1.0)
        itemRes1.exTimeSpent.get should be(0)
        itemRes1.res.get.size should be(0)
        itemRes1.resValues.get.size should be(0)
        itemRes1.score should be(0)
        itemRes1.time_stamp should be(1515404395752L)
        itemRes1.pass should be("No")
        itemRes1.qtitle.get should be("BigKeys")
        itemRes1.qdesc.get should be("")
    }
    
    it should "generate 5 workflow summary" in {
        val data = loadFile[V3Event]("src/test/resources/workflow-summary/test-data2.log")
        val out = WorkFlowSummaryModel.execute(data, None)
        out.count() should be(5)
        
        val me = out.collect();
        val appSummaryEvent1 = me.filter { x => x.dimensions.`type`.get.equals("app") }
        val sessionSummaryEvent1 = me.filter { x => x.dimensions.`type`.get.equals("session") }
        val playerSummaryEvent1 = me.filter { x => x.dimensions.`type`.get.equals("player") }
        val editorSummaryEvent1 = me.filter { x => x.dimensions.`type`.get.equals("editor") }
        
        appSummaryEvent1.size should be(0)
        sessionSummaryEvent1.size should be(1)
        playerSummaryEvent1.size should be(1)
        editorSummaryEvent1.size should be(3)
    }

    it should "generate 3 workflow summary" in {
        val data = loadFile[V3Event]("src/test/resources/workflow-summary/test-data3.log")
        val out = WorkFlowSummaryModel.execute(data, None)
        out.count() should be(3)
        
        val me = out.collect();
        val appSummaryEvent1 = me.filter { x => x.dimensions.`type`.get.equals("app") }
        val sessionSummaryEvent1 = me.filter { x => x.dimensions.`type`.get.equals("session") }
        val playerSummaryEvent1 = me.filter { x => x.dimensions.`type`.get.equals("player") }
        val editorSummaryEvent1 = me.filter { x => x.dimensions.`type`.get.equals("editor") }
        
        appSummaryEvent1.size should be(2)
        sessionSummaryEvent1.size should be(1)
        playerSummaryEvent1.size should be(0)
        editorSummaryEvent1.size should be(0)

        val event1 = appSummaryEvent1.filter(f => f.mid.equals("3F3079D39311560F56979A68BEC1055D")).last
        
        // Validate for event envelope
        event1.eid should be("ME_WORKFLOW_SUMMARY");
        event1.context.pdata.model.get should be("WorkflowSummarizer");
        event1.context.pdata.ver should be("1.0");
        event1.context.granularity should be("SESSION");
        event1.context.date_range should not be null;
        event1.dimensions.`type`.get should be("app");
        event1.dimensions.did.get should be("11573c50cae2078e847f12c91a2d1965eaa73714");
        event1.dimensions.sid.get should be("830811c2-3c02-4c45-8755-3f15064a88a2");
        event1.dimensions.mode.getOrElse("") should be("")

        val summary1 = JSONUtils.deserialize[WorkflowDataRead](JSONUtils.serialize(event1.edata.eks));
        summary1.interact_events_per_min should be(1.0);
        summary1.start_time should be(1515402354310L);
        summary1.interact_events_count should be(1);
        summary1.end_time should be(1515402366478L);
        summary1.time_diff should be(12.17);
        summary1.time_spent should be(12.17);
        summary1.item_responses.get.size should be(0);
        summary1.page_summary.get.size should be(0);
        summary1.env_summary.get.size should be(0);
        summary1.events_summary.get.size should be(2);
        summary1.telemetry_version should be("3.0");
        
        val esList1 = summary1.events_summary.get
        esList1.size should be(2);
        val esMap1 = esList1.map { x => (x.id, x.count) }.toMap
        esMap1.get("INTERACT").get should be(1);
        esMap1.get("START").get should be(2);

        val event2 = sessionSummaryEvent1.head
        
        // Validate for event envelope
        event2.eid should be("ME_WORKFLOW_SUMMARY");
        event2.context.pdata.model.get should be("WorkflowSummarizer");
        event2.context.pdata.ver should be("1.0");
        event2.context.granularity should be("SESSION");
        event2.context.date_range should not be null;
        event2.dimensions.`type`.get should be("session");
        event2.dimensions.did.get should be("11573c50cae2078e847f12c91a2d1965eaa73714");
        event2.dimensions.sid.get should be("830811c2-3c02-4c45-8755-3f15064a88a2");
        event1.dimensions.mode.getOrElse("") should be("")

        val summary2 = JSONUtils.deserialize[WorkflowDataRead](JSONUtils.serialize(event2.edata.eks));
        summary2.interact_events_per_min should be(1.0);
        summary2.start_time should be(1515402354782L);
        summary2.interact_events_count should be(1);
        summary2.end_time should be(1515402366478L);
        summary2.time_diff should be(11.7);
        summary2.time_spent should be(11.7);
        summary2.item_responses.get.size should be(0);
        summary2.page_summary.get.size should be(0);
        summary2.env_summary.get.size should be(0);
        summary2.events_summary.get.size should be(2);
        summary2.telemetry_version should be("3.0");
        
        val esList2 = summary2.events_summary.get
        esList2.size should be(2);
        val esMap2 = esList2.map { x => (x.id, x.count) }.toMap
        esMap2.get("INTERACT").get should be(1);
        esMap2.get("START").get should be(1);
    }
}