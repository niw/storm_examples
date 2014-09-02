import java.net.InetSocketAddress
import java.util

import backtype.storm.task.IMetricsContext
import backtype.storm.tuple.{Fields, Values}
import org.atilika.kuromoji.Tokenizer
import storm.trident.TridentTopology
import storm.trident.operation.builtin.Count
import storm.trident.operation.{BaseFunction, TridentCollector}
import storm.trident.state.{State, StateFactory}
import storm.trident.tuple.TridentTuple
import trident.memcached.MemcachedState
import twitter4j._

import scala.collection.JavaConverters._
import scala.util.control.Exception._

object MemcachedSateFactory {
  val makeStateMethod = classOf[MemcachedState[_]].getDeclaredClasses.flatMap { klass =>
    allCatch opt klass.getMethod("makeState", classOf[util.Map[_, _]], Integer.TYPE, Integer.TYPE)
  }.headOption getOrElse {
    throw new RuntimeException("Couldn't find expected maksState method.")
  }
}

class MemcachedSateFactory(host: String, port: Int) extends StateFactory {
  import MemcachedSateFactory._

  val factory = MemcachedState.nonTransactional(List(
    new InetSocketAddress(host, port)
  ).asJava)

  // Since original MemcachedState.Factory has version incompatible makeState method,
  // Use reflection to patch it dynamically.
  def makeState(conf: util.Map[_, _], metrics: IMetricsContext, partitionIndex: Int, numPartitions: Int) = {
    makeStateMethod.invoke(factory, conf, partitionIndex: java.lang.Integer, numPartitions: java.lang.Integer) match {
      case state: State => state
      case _ => throw new RuntimeException("Return type mismatch from makeState.")
    }
  }
}

object TweetTokenCountTopology {
  // Tokenizer is not serializable, then put it in object to be static.
  val tokenizer = Tokenizer.builder().build()
}

class TweetTokenCountTopology(args: Args) extends TopologyFactory(args) {
  import TweetTokenCountTopology._

  def topology = {
    // val stateFactory = new MemoryMapState.Factory
    val stateFactory = new MemcachedSateFactory("localhost", 11211)

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
