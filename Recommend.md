#### https://blog.51cto.com/u_14637764/2509065
### kafka 建议监控的线程
Log Compaction 线程，这类线程是以 kafka-log-cleaner-thread 开头的。就像前面提到的，此线程是做日志 Compaction的。
一旦它挂掉了，所有 Compaction 操作都会中断，但用户对此通常是无感知的。
副本拉取消息的线程，通常以 ReplicaFetcherThread 开头。这类线程执行 Follower 副本向 Leader 副本拉取消息的逻辑。如果它们挂掉了，系统会表现为对应的 Follower 副本不再从 Leader 副本拉取消息，因而 Follower 副本的 Lag 会越来越大。
不论你是使用 jstack 命令，还是其他的监控框架，我建议你时刻关注 Broker 进程中这两类线程的运行状态。一旦发现它们状态有变，就立即查看对应的 Kafka 日志，定位原因，因为这通常都预示会发生较为严重的错误。

###  client和broker延迟太大
  * 如果Clients与Broker的网络延迟很大（如RTT>10ms）,建议调大控制缓冲区参数 sendBufferSize和recvBufferSize, 100k太小了


