import backtype.storm.generated.StormTopology
import backtype.storm.{LocalCluster, StormSubmitter, Config}

class Args(args: Map[String, List[String]]) {
  def list(key: String): List[String] = args.get(key) getOrElse Nil

  def boolean(key: String): Boolean = args.contains(key)

  def option(key: String): Option[String] = args.get(key).flatMap(_.headOption)

  def apply(key: String) = option(key)
}

object Args {
  def apply(args: Iterable[String]) = {
    new Args(
      args.foldLeft(List("" -> List[String]())) { (args, arg) =>
        if (arg.startsWith("-")) {
          val key = arg.dropWhile(_ == '-')
          (key -> Nil) :: args
        } else {
          (args.head._1 -> (arg :: args.head._2)) :: args
        }
      }.reverse.toMap
    )
  }
}

abstract class TopologyFactory(args: Args) {
  val name = args("name").getOrElse("test")

  def topology: StormTopology
}

class Submit(className: String, args: Args) {
  private lazy val factory = {
    Class.forName(className)
        .getConstructor(classOf[Args])
        .newInstance(args)
        .asInstanceOf[TopologyFactory]
  }

  private lazy val config = {
    val conf = new Config
    conf.setDebug(args.boolean("debug"))
    conf.setNumWorkers(args.option("workers").map(_.toInt) getOrElse 2)
    conf
  }

  def apply() {
    if (args.boolean("local")) {
      val cluster = new LocalCluster()
      cluster.submitTopology(factory.name, config, factory.topology)
      Thread.sleep(args.option("sleep").map(_.toLong) getOrElse 5000)
      cluster.killTopology(factory.name)
      cluster.shutdown()
    } else {
      StormSubmitter.submitTopology(factory.name, config, factory.topology)
    }
  }
}

object Main {
  def main(args: Array[String]) {
    args match {
      case Array(className, args @ _*) =>
        val submit = new Submit(className, Args(args))
        submit()
      case _ =>
        println("Usage: storm jar_name Main topology_factory_name [args]")
        System.exit(1)
    }
  }
}
