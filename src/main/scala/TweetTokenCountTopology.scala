import java.net.InetSocketAddress
import org.atilika.kuromoji.Tokenizer
import storm.trident.operation.builtin.Count
import storm.trident.TridentTopology
import storm.trident.operation.{BaseFunction, TridentCollector}
import storm.trident.tuple.TridentTuple
import trident.memcached.MemcachedState
import scala.collection.JavaConverters._
import backtype.storm.Config
import backtype.storm.spout.SpoutOutputCollector
import backtype.storm.task.TopologyContext
import backtype.storm.topology.base.BaseRichSpout
import backtype.storm.topology.OutputFieldsDeclarer
import backtype.storm.tuple.{Fields, Values}
import java.util.concurrent.LinkedBlockingQueue
import twitter4j.conf.ConfigurationBuilder
import twitter4j._

case class TwitterOAuthToken(token: String, secret: String) {
  def key = token
}

class TwitterSampleSpout(accessToken: TwitterOAuthToken, consumer: TwitterOAuthToken) extends BaseRichSpout {
  val queue = new LinkedBlockingQueue[Status]
  var collector: Option[SpoutOutputCollector] = None

  val configuration = new ConfigurationBuilder()
      .setOAuthAccessToken(accessToken.token)
      .setOAuthAccessTokenSecret(accessToken.secret)
      .setOAuthConsumerKey(consumer.key)
      .setOAuthConsumerSecret(consumer.secret)
      .build()

  val factory = new TwitterStreamFactory(configuration)
  var stream: Option[TwitterStream] = None

  def twitterStream = {
    val listener = new StatusListener {
      def onStatus(status: Status) {
        queue.offer(status)
      }

      def onDeletionNotice(p1: StatusDeletionNotice) {}
      def onTrackLimitationNotice(p1: Int) {}
      def onScrubGeo(p1: Long, p2: Long) {}
      def onStallWarning(p1: StallWarning) {}
      def onException(p1: Exception) {}
    }

    val stream = factory.getInstance()
    stream.addListener(listener)
    stream.sample()
    stream
  }

  def open(conf: java.util.Map[_, _], context: TopologyContext, output: SpoutOutputCollector) {
    collector = Some(output)
    stream = Some(twitterStream)
  }

  override def close() {
    stream.foreach(_.shutdown())
  }

  def nextTuple() {
    (for(status <- Option(queue.poll()); collector <- collector) yield {
      collector.emit(new Values(status))
    }) getOrElse {
      Thread.sleep(100)
    }
  }

  def declareOutputFields(declarer: OutputFieldsDeclarer) {
    declarer.declare(new Fields("tweet"))
  }

  override def getComponentConfiguration: java.util.Map[String, AnyRef] = {
    val config = new Config
    config.setMaxTaskParallelism(1)
    config
  }
}

object TweetTokenCountTopology {
  // Tokenizer is not serializable, then put it in object to be static.
  val tokenizer = Tokenizer.builder().build()
}

class TweetTokenCountTopology(args: Args) extends TopologyFactory(args) {
  import TweetTokenCountTopology._

  val accessToken = (args("accessToken"), args("accessSecret")) match {
    case (Some(token), Some(secret)) =>
      TwitterOAuthToken(token, secret)
    case _ =>
      throw new IllegalArgumentException("Missing --accessToken or --accessSecret")
  }

  val consumer = (args("consumerKey"), args("consumerSecret")) match {
    case (Some(key), Some(secret)) =>
      TwitterOAuthToken(key, secret)
    case _ =>
      throw new IllegalArgumentException("Missing --consumerKey or --consumerSecret")
  }

  def topology = {
    val sampleSpout = new TwitterSampleSpout(accessToken, consumer)

    // val stateFactory = new MemoryMapState.Factory
    val stateFactory = MemcachedState.nonTransactional(List(
      new InetSocketAddress("localhost", 11211)
    ).asJava)


    val topology = new TridentTopology()
    topology.newStream("tweets", sampleSpout)
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
