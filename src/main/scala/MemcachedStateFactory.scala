import java.net.InetSocketAddress
import java.util

import backtype.storm.task.IMetricsContext
import storm.trident.state.{State, StateFactory}
import trident.memcached.MemcachedState

import scala.util.control.Exception._
import scala.collection.JavaConverters._

object MemcachedStateFactory {
  val makeStateMethod = classOf[MemcachedState[_]].getDeclaredClasses.flatMap { klass =>
    allCatch opt klass.getMethod("makeState", classOf[util.Map[_, _]], Integer.TYPE, Integer.TYPE)
  }.headOption getOrElse {
    throw new RuntimeException("Couldn't find expected maksState method.")
  }
}

class MemcachedStateFactory(host: String, port: Int) extends StateFactory {
  import MemcachedStateFactory._

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