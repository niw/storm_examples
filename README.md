Simple Storm Examples
=====================

This project contains very simple [Storm](https://github.com/nathanmarz/storm) examples using Scala and instructions, helper configuration to play with it.

Getting started
---------------

Install [maven](http://maven.apache.org/), [zinc](https://github.com/typesafehub/zinc) (not required, but recommended.)

    $ brew install maven
    $ brew install zinc

Use Java 1.7 instead of OS X default 1.6. Download it from [Oracle](http://www.oracle.com/technetwork/java/javase/downloads/index.html) and install it. To switch ``java`` to 1.7, use next command to set ``JAVA_HOME``.

    $ export JAVA_HOME=`/usr/libexec/java_home -v 1.7`

Unfortunately, a dependency [``trident-memcached``](https://github.com/nathanmarz/trident-memcached) which provides an access to ``memcached`` to store results from storm is not provided yet from the repository. Following steps to install it into the local repository at ``~/.m2``.

    $ brew install lein
    $ git clone https://github.com/nathanmarz/trident-memcached.git
    $ cd trident-memcached
    $ lein install
    $ ls -al ~/.m2/repository/storm/trident-memcached

Now we're ready to build examples. This ``mvn`` command will download all dependencies includes Strom itself.

    $ mvn -Pzinc clean compile

Run [a simple Topology](https://github.com/niw/storm_examples/blob/master/src/main/scala/AppendExclamationsTopology.scala) which adds ``!!!`` to the stream input randomly generated in the sample code.

    $ java -cp `cat .classpath.txt`:target/classes Main AppendExclamationsTopology --local --debug

There is also another famous example, [WordCount](https://github.com/niw/storm_examples/blob/master/src/main/scala/WordCountTopology.scala).

    $ java -cp `cat .classpath.txt`:target/classes Main WordCountTopology --local --debug

Use the class path includes all dependencies and run simple [``Main``](https://github.com/niw/storm_examples/blob/master/src/main/scala/Main.scala) class to start Storm Topology.
``--local`` option runs Topology locally using ``backtype.storm.LocalCluster`` without submitting job to Storm cluster. ``--debug`` option enables ``setDebug(true)`` for ``backtype.storm.Config``.

Run Topology on Storm cluster
-----------------------------

### Install Strom on your local host

If you already have Storm, skip this section.

There are no easy way like Hadoop distributions to install Storm, we can use binary distribution to install Storm.

First, install Strom. I recommend to install it to ``/usr/local/strom``.
Download the latest Strom from [download page](http://storm-project.net/downloads.html).

    $ cd /usr/local
    $ tar xzvf apache-storm-0.9.2-incubating.tar.gz
    $ ln -s /usr/local/apache-storm-0.9.2-incubating storm
    $ export PATH=$PATH:/usr/local/storm/bin

Install [Zookeeper](http://zookeeper.apache.org/), on which Storm also depends.
Download the latest Zookeeper from [download page](http://www.apache.org/dyn/closer.cgi/zookeeper/).

    $ cd /usr/local
    $ tar xzvf zookeeper-3.4.6.tar.gz
    $ ln -s /usr/local/zookeeper-3.4.6 zookeeper
    $ export PATH=$PATH:/usr/local/zookeeper/bin

Configure ``/usr/local/zookeeper/conf/zoo.cfg``.

    $ cd /usr/local/zookeeper
    $ cp conf/zoo_sample.cfg conf/zoo.cfg

Just use ``zoo_sample.cfg`` here, since it runs Zookeeper on localhost.
Normally, as the comment in the file said, we actually need at least 3 instances of Zookeeper, but to run it on localhost, a single Zookeeper instance is fine.

Configure ``/usr/local/storm/conf/storm.yaml`` to make it work on local host.

    storm.local.dir: /tmp/storm
    java.library.path: /usr/local/storm/lib:/usr/local/lib:/usr/lib

There are implicit default settings. We don't need to add these to ``storm.yaml``, but remember, we're using local Zookeeper and running Storm Nimbus on localhost.

    storm.zookeeper.servers:
      - localhost
    nimbus.host: localhost

We're ready. Run Zookeeper, Storm Nimbus, Storm Supervisor and Storm UI.

    $ zkServer.sh start-foreground
    $ storm nimbus
    $ storm supervisor
    $ storm ui

Zookeeper keeps all states for Storm in fault-tolerance.
Numbus is like Hadoop Jobtracker, managing all Topology on the cluster.
Supervisor is like Hadoop TaskTracker, managing all jobs running on each hosts.
Storm UI provides a web interface at <http://localhost:8080/> to see current cluster conditions.

### Run Topology on Storm cluster

Without ``--local``, the ``Main`` class will submit the Topology to Nimbus instead of running it locally.
To send a Topology to Storm, at this time, build a jar file and use ``storm`` command.

    $ mvn -Pzinc package
    $ storm jar target/storm_examples-0.1.0-SNAPSHOT-all.jar Main WordCountTopology --name word_count

``--name`` option gives a name to the Topology instead of `test`. Since we can run multiple Topology on the cluster, we need a name to identify them.

Unlike Hadoop jobs, or running Topology locally, Storm keeps running Topology forever. To kill these Topology, use [Storm UI](http://localhost:8080/) or ``storm`` command.

    $ storm list
    $ storm kill word_count

### Run Topology on real Storm cluster

Put ``~/.storm/storm.yaml`` to select a production Storm cluster.
We can use same command used for local Storm cluster to submit a Topology.

    numbus.host: production.numbus.host

### Store results in Memcached

To store the topology results, we need a specific storage to keep it in persistent. For example, [`TweetTokenCountTopology`](https://github.com/niw/storm_examples/blob/master/src/main/scala/TweetTokenCountTopology.scala) topology stores results in [Memcached](http://memcached.org/) and keeps counting up.

    $ memcached -vvv -p 11211
    $ storm jar target/storm_examples-0.1.0-SNAPSHOT-all.jar Main TweetTokenCountTopology \
      --name tweet_token_count \
      --accessToken YOUR_API_ACCESS_TOKEN \
      --accessSecret YOUR_API_ACCESS_TOKEN_SECRET \
      --consumerKey YOUR_API_CONSUMER_KEY \
      --consumerSecret YOUR_API_CONSUMER_SECRET

This Topology fetches tweets from Twitter Streaming API, [tokenize Japanese tweets](http://www.atilika.org/) and count the tokens (because Japanese doesn't have a space between words, we need to tokenize before counting words.) Then, store results in Memcached.
You can grab these Access Token and Consumer Key at [Twitter Developers](https://dev.twitter.com/).

To see results, simply ``telnet`` to Memcached then dump it.

    $ (echo 'stats items'; sleep 1) | telnet localhost 11211
    $ (echo 'stats cachedump 1 100'; sleep 1) | telnet localhost 11211

This example is using [Trident](https://github.com/nathanmarz/storm/wiki/Trident-tutorial) library which provides a set of classes to built Topology easily like using [Cascading](http://www.cascading.org/) for Hadoop.
