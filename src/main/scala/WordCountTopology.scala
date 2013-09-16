import backtype.storm.spout.SpoutOutputCollector
import backtype.storm.task.TopologyContext
import backtype.storm.topology.base.{BaseBasicBolt, BaseRichSpout}
import backtype.storm.topology.{BasicOutputCollector, OutputFieldsDeclarer, TopologyBuilder}
import backtype.storm.tuple.{Tuple, Fields, Values}

class SentenceSpout extends BaseRichSpout {
  var collector: Option[SpoutOutputCollector] = None

  def open(conf: java.util.Map[_, _], context: TopologyContext, output: SpoutOutputCollector) {
    collector = Some(output)
  }

  def nextTuple() {
    collector.foreach(_.emit(new Values("nyan nyan meow purr")))
  }

  def declareOutputFields(declarer: OutputFieldsDeclarer) {
    declarer.declare(new Fields("sentence"))
  }
}

class SplitBolt extends BaseBasicBolt {
  def execute(tuple: Tuple, collector: BasicOutputCollector) {
    val sentence = tuple.getString(0)
    val words = sentence.toLowerCase.replaceAll("[^a-zA-Z0-9\\s]", "").split("\\s+")

    for(word <- words) {
      collector.emit(new Values(word))
    }
  }

  def declareOutputFields(declarer: OutputFieldsDeclarer) {
    declarer.declare(new Fields("word"))
  }
}

class SumBolt extends BaseBasicBolt {
  val counts = new scala.collection.mutable.HashMap[String, Int]

  def execute(tuple: Tuple, collector: BasicOutputCollector) {
    val word = tuple.getString(0)
    val sum = counts.getOrElse(word, 0) + 1
    counts += word -> sum
    collector.emit(new Values(word, int2Integer(sum)))
  }

  def declareOutputFields(declarer: OutputFieldsDeclarer) {
    declarer.declare(new Fields("word", "count"))
  }
}

class WordCountTopology(args: Args) extends TopologyFactory(args) {
  def topology = {
    val builder = new TopologyBuilder
    builder.setSpout("sentences", new SentenceSpout)
    builder.setBolt("split", new SplitBolt)
           .shuffleGrouping("sentences")
    builder.setBolt("sum", new SumBolt)
           .fieldsGrouping("split", new Fields("word"))
    builder.createTopology()
  }
}
