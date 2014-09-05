import backtype.storm.tuple.{Fields, Values}
import org.atilika.kuromoji.Tokenizer
import storm.trident.TridentTopology
import storm.trident.operation.builtin.Count
import storm.trident.operation.{BaseFunction, TridentCollector}
import storm.trident.tuple.TridentTuple
import twitter4j._

import scala.collection.JavaConverters._

object TweetTokenCountTopology {
  // Tokenizer is not serializable, then put it in object to be static.
  val tokenizer = Tokenizer.builder().build()
}

class TweetTokenCountTopology(args: Args) extends TopologyFactory(args) {
  import TweetTokenCountTopology._

  def topology = {
    val stateFactory = new MemcachedStateFactory("localhost", 11211)

    val spoutFactory = new TweetSampleSpoutFactory(args)

    val topology = new TridentTopology()
    topology.newStream("tweets", spoutFactory.spout)
      .each(
        new Fields("tweet"),
        new BaseFunction {
          def tokens(text: String) = {
            tokenizer.tokenize(text).asScala withFilter { token =>
              token.getAllFeaturesArray match {
                case Array("名詞", "一般", _*) => true
                case _ => false
              }
            } map {
              _.getSurfaceForm
            }
          }

          def execute(tuple: TridentTuple, collector: TridentCollector) {
            tuple.get(0) match {
              case status: Status if status.getUser.getLang == "ja" =>
                for (token <- tokens(status.getText)) {
                  collector.emit(new Values(token))
                }
              case _ =>
            }
          }
        },
        new Fields("token")
      )
      .groupBy(new Fields("token"))
      .persistentAggregate(stateFactory, new Count(), new Fields("count"))

    topology.build()
  }
}
