import java.util.concurrent.LinkedBlockingQueue

import backtype.storm.Config
import backtype.storm.spout.SpoutOutputCollector
import backtype.storm.task.TopologyContext
import backtype.storm.topology.OutputFieldsDeclarer
import backtype.storm.topology.base.BaseRichSpout
import backtype.storm.tuple.{Fields, Values}
import twitter4j._
import twitter4j.conf.ConfigurationBuilder

case class TwitterOAuthToken(token: String, secret: String) {
  def key = token
}

class TweetSampleSpout(accessToken: TwitterOAuthToken, consumer: TwitterOAuthToken) extends BaseRichSpout {
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

class TweetSampleSpoutFactory(args: Args) {
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

  def spout = new TweetSampleSpout(accessToken, consumer)
}