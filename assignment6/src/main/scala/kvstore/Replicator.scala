package kvstore

import akka.actor.{ReceiveTimeout, Props, Actor, ActorRef}
import scala.concurrent.duration._
import common.Once

object Replicator {
  case class Replicate(key: String, valueOption: Option[String], id: Long)
  case class Replicated(key: String, id: Long)
  
  case class Snapshot(key: String, valueOption: Option[String], seq: Long)
  case class SnapshotAck(key: String, seq: Long)

  def props(replica: ActorRef): Props = Props(new Replicator(replica))
}

class Replicator(val replica: ActorRef) extends Actor {
  import Replicator._
  import Replica._
  import context.dispatcher
  
  /*
   * The contents of this actor is just a suggestion, you can implement it in any way you like.
   */

  // a sequence of not-yet-sent snapshots (you can disregard this if not implementing batching)
  var pending = Vector.empty[Snapshot]
  
  var _seqCounter = 0L
  def nextSeq = {
    val ret = _seqCounter
    _seqCounter += 1
    ret
  }
  
  /* TODO Behavior for the Replicator. */
  def receive: Receive = {
    case r@Replicate(key,value,id) => {
      println(s"Received replicate msg $r")
      replicate(key,value,id,nextSeq,sender)
    }
  }

  private[this] def replicate(key: String, value: Option[String], id: Long, seq: Long, origin: ActorRef) : Unit = {
    println(s"Replicating.... to $replica")
    replica.tell(Snapshot(key, value, seq),Once.withTimeout(10000.milliseconds){
       case sa:SnapshotAck => ack(origin,sa)
       case ReceiveTimeout => replicate(key,value,id,seq,origin)
     })
  }

  private[this] def ack(origin: ActorRef, ack: SnapshotAck) = {
    println(s"Sending ack $ack")
    origin ! ack
  }

}
