import backtype.storm.testing.TestWordSpout
import backtype.storm.topology.base.BaseBasicBolt
import backtype.storm.topology.{BasicOutputCollector, OutputFieldsDeclarer, TopologyBuilder}
import backtype.storm.tuple.{Fields, Values, Tuple}

class AppendExclamationsBolt extends BaseBasicBolt {
  def execute(tuple: Tuple, collector: BasicOutputCollector) {
    collector.emit(new Values(tuple.getString(0) + "!!!"))
  }

  def declareOutputFields(declarer: OutputFieldsDeclarer) {
    declarer.declare(new Fields("word"))
  }
}

class AppendExclamationsTopology(args: Args) extends TopologyFactory(args) {
  def topology = {
    val builder = new TopologyBuilder
    builder.setSpout("words", new TestWordSpout, 2)
    builder.setBolt("exclamation", new AppendExclamationsBolt, 1)
           .shuffleGrouping("words")
    builder.createTopology()
  }
}
