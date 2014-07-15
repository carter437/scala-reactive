package kvstore

import akka.actor._
import kvstore.Arbiter._
import scala.collection.immutable.Queue
import akka.actor.SupervisorStrategy.{Resume, Restart}
import scala.annotation.tailrec
import akka.pattern.{ ask, pipe }
import scala.concurrent.duration._
import akka.util.Timeout
import common.Once
import akka.actor.ActorDSL._
import kvstore.Arbiter.Replicas
import scala.Some
import akka.actor.OneForOneStrategy
import kvstore.Arbiter.Replicas


object Replica {
  sealed trait Operation {
    def key: String
    def id: Long
  }
  case class Insert(key: String, value: String, id: Long) extends Operation
  case class Remove(key: String, id: Long) extends Operation
  case class Get(key: String, id: Long) extends Operation

  sealed trait OperationReply
  case class OperationAck(id: Long) extends OperationReply
  case class OperationFailed(id: Long) extends OperationReply
  case class GetResult(key: String, valueOption: Option[String], id: Long) extends OperationReply

  def props(arbiter: ActorRef, persistenceProps: Props): Props = Props(new Replica(arbiter, persistenceProps))
}

class Replica(val arbiter: ActorRef, persistenceProps: Props) extends Actor {
  import Replica._
  import Replicator._
  import Persistence._
  import context.dispatcher

  var _seqCounter = 0L
  def nextSeq = {
    val ret = _seqCounter
    _seqCounter += 1
    ret
  }

  var persister: ActorRef = _
  /*
   * The contents of this actor is just a suggestion, you can implement it in any way you like.
   */
  var currSnapShotId = 0L

  var kv = Map.empty[String, String]
  // a map from secondary replicas to replicators
  var secondaries = Map.empty[ActorRef, ActorRef]
  // the current set of replicators
  var replicators = Set.empty[ActorRef]

  def receive = {
    case JoinedPrimary   => context.become(leader)
    case JoinedSecondary => context.become(replica)
  }

  /* TODO Behavior for  the leader role. */
  val leader: Receive = {
    case op@Insert(key,value,id)  => updates(key,Some(value),id,sender)
    case op@Remove(key,id)        => updates(key,None,id,sender)

    case op:Get => get(op,sender)
    case Replicas(replicas) => {
      val replicaList = replicas.filterNot(_ == self).toList
      val replicatorList = replicaList.map( r=> context.actorOf(Props(new Replicator(r))))
      replicaList.zip(replicatorList).foreach{ case (k,v) =>
        context.watch(k)
        secondaries = secondaries + (k -> v)
      }
      replicators= replicators ++ replicatorList

      kv.foreach{ case (k,v) =>
        val id = nextSeq
        replicators.foreach{ r =>
           r ! Replicate(k,Some(v),id)
        }
      }
    }
    case Terminated(dead) => {
      secondaries.get(dead).foreach{ r =>
        secondaries = secondaries - dead
        r ! PoisonPill
      }
    }

    case msg=>println(s"Whoops Leader can't handle this message $msg")
  }

  /* TODO Behavior for the replica role. */
  val replica: Receive = {
    case op:Get =>  get(op,sender)
    case s@Snapshot(key,value,seq)  if currSnapShotId == seq=> {
      println(s"Replica $self received Snapshot Message. Good Sequence")
      currSnapShotId = s.seq + 1
      val ack = SnapshotAck(key,seq)
      persist(key,value,seq,sender,ack)
    }
    case Snapshot(_,_,seq: Long)  if seq > currSnapShotId => {
      println(s"Replica $self received Snapshot Message. Hi Sequence")
    }
    case Snapshot(key,value,seq)  if seq < currSnapShotId => {
      println(s"Replica $self received Snapshot Message. Low Sequence")
      sender ! SnapshotAck(key,seq)
    }
    case msg => println(s"Whoops...... $msg $currSnapShotId")
  }

  private[this] def updates(key: String, value: Option[String], id: Long, origin: ActorRef) = {
    println(s"Update Received for $key $value")
    val currReps = replicators
    val once = Once.withTimeout(1.second){
      case ReceiveTimeout => origin ! OperationFailed(id)
      case msg => replicate(key,value,id,currReps,origin)
    }
    persist(key,value,id,once,OperationAck(id))
  }

  private[this] def persist(key: String, value: Option[String], id: Long, origin: ActorRef, ack: Any) : Unit = {
    val msg = Persist(key,value,id)
    value.fold(remove(key))(insert(key,_))


    val tempActor = actor( new Act{
      var latestSender: ActorRef = _
      become{
        case p@Persist(_,_,_) => {
          latestSender = sender
          println(s"Sending $p to Persister")
          persister ! p
        }
        case p@Persisted(key,id) => {
          println(s"Got Persisted Back $p")
          origin ! ack
          latestSender ! p //send any message to not timeout
          context.stop(self)
        }
      }
    })

    poll(msg,tempActor,100.milliseconds)

  }

  private[this] def poll(msg: Any, recp: ActorRef, duration: Duration) : Unit = {
    val Tick = "tick"

    def cancellable(ticker:ActorRef) = {
      context.system.scheduler.schedule(0.milliseconds,
      100.milliseconds,
      ticker,
      Tick)
    }

     val tick = actor( new Act{
       val cancel: Cancellable = cancellable(self)
       become{
        case Tick =>  {
          recp ! msg
        }
        case Terminated(dead) if dead == recp => {
            cancel.cancel()
            context.stop(self)
        }
      }
    })
  }

  private[this] def get(get: Get, sender: ActorRef) = {
    sender ! GetResult(get.key, kv.get(get.key),get.id)
  }

  private[this] def insert(key: String, value: String) : Unit = {
    kv = kv + (key -> value)
  }

  private[this] def remove(key: String) : Unit = {
    kv = kv.filterKeys(_ != key)
  }

  private[this] def replicate(key:String, value:Option[String],id:Long, replicators: Set[ActorRef], origin: ActorRef) : Unit = {
    if(replicators.isEmpty){
       println("Replicas are Empty")
       origin ! OperationAck(id)
    }
    else{
      println(s"Replica Count ${replicators.size}")
      val Go = "Go"
      val Success = "Success"
      var replicatorsLeft = replicators.size

      val tempAct = actor( new Act{
        var origin: ActorRef = _
        become{
          case Go => {
            origin = sender
            replicators.foreach{r =>
              println(s"Called Go with ${replicators.mkString}")
              r ! Replicate(key,value,id)
            }
          }
          case s@SnapshotAck(key,id) => {
            replicatorsLeft = replicatorsLeft - 1
            println(s"Recieved Snapshot Ack $replicatorsLeft")
            if(replicatorsLeft == 0) {
              origin ! Success
            }
          }
          case msg => println(s"Umm wasn't expecting this $msg")
        }
      })

      tempAct.tell(Go,Once.withTimeout(1.second){
        case Success => {
          println("TempAct Success")
          origin ! OperationAck(id)
          context.stop(tempAct)
        }
        case ReceiveTimeout => {
          origin ! OperationFailed(id)
          context.stop(tempAct)
        }
        case msg => println(s"2. Umm wasn't expecting this $msg")
      })

    }
  }


  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy(){
    case ex:PersistenceException => Resume
  }

  override def preStart(): Unit = {
    arbiter ! Join
    persister = context.actorOf(persistenceProps)
    super.preStart()
  }
}
