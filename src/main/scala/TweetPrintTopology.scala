import backtype.storm.generated.StormTopology
import backtype.storm.topology.base.BaseBasicBolt
import backtype.storm.topology.{BasicOutputCollector, OutputFieldsDeclarer, TopologyBuilder}
import backtype.storm.tuple.Tuple

class PrintBold extends BaseBasicBolt {
  def execute(tuple: Tuple, collector: BasicOutputCollector) {
    println(tuple)
  }

  def declareOutputFields(declarer: OutputFieldsDeclarer) {
  }
}

class TweetPrintTopology(args: Args) extends TopologyFactory(args) {
  def topology: StormTopology = {
    val spoutFactory = new TweetSampleSpoutFactory(args)

    val builder = new TopologyBuilder
    builder.setSpout("tweet", spoutFactory.spout)
    builder.setBolt("print", new PrintBold, 2)
           .shuffleGrouping("tweet")
    builder.createTopology()
  }
}
