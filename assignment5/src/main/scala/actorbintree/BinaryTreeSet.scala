/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package actorbintree

import akka.actor._
import scala.collection.immutable.Queue
import akka.event.LoggingReceive
import scala.concurrent.duration.Duration
import akka.actor.Actor._
import scala.Some
import scala.Some
import java.util.concurrent.atomic.AtomicInteger
import java.util.Date

object BinaryTreeSet {

  trait Operation {
    def requester: ActorRef
    def id: Int
    def elem: Int
  }

  trait OperationReply {
    def id: Int
  }

  /** Request with identifier `id` to insert an element `elem` into the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Insert(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to check whether an element `elem` is present
    * in the tree. The actor at reference `requester` should be notified when
    * this operation is completed.
    */
  case class Contains(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to remove the element `elem` from the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Remove(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request to perform garbage collection*/
  case object GC

  /** Holds the answer to the Contains request with identifier `id`.
    * `result` is true if and only if the element is present in the tree.
    */
  case class ContainsResult(id: Int, result: Boolean) extends OperationReply
  
  /** Message to signal successful completion of an insert or remove operation. */
  case class OperationFinished(id: Int) extends OperationReply

  case object Replay

}


class BinaryTreeSet extends Actor with Stash {
  import BinaryTreeSet._
  import BinaryTreeNode._

  def createRoot: ActorRef = context.actorOf(BinaryTreeNode.props(0, initiallyRemoved = true))

  var root = createRoot

  // optional
  var pendingQueue = Queue.empty[Operation]

  // optional
  def receive = LoggingReceive(normal)

  // optional
  /** Accepts `Operation` and `GC` messages. */
  val normal: Receive = {
    case msg@Insert(_,id,_) => { root ! msg}
    case msg@Contains(_,id,_) => { root ! msg}
    case msg@Remove(_,id,_) => { root ! msg}
    case msg@GC => { println("================== GC ==================== " + msg)
      context.become(garbageCollecting)
      val newRoot = createRoot
      val outerCtx = context
      root.tell(CopyTo(newRoot),Once{
        case CopyFinished => {
          root ! PoisonPill
          root = newRoot
          //unstashAll()
          outerCtx.self ! Replay
        }
      })
    }
  }

  def garbageCollecting: Receive = {
    case GC => //drop
    case Replay => {
      pendingQueue.foreach(root ! _)
      pendingQueue = Queue.empty[Operation]
      context.unbecome()
    }
    case op:Operation => pendingQueue = pendingQueue.enqueue(op)//stash()
  }

  override def supervisorStrategy: SupervisorStrategy = {
    AllForOneStrategy() {
      case e: Throwable => {
        e.printStackTrace
        SupervisorStrategy.Stop
      }
    }
  }
}

object BinaryTreeNode {
  trait Position

  case object Left extends Position
  case object Right extends Position

  case class CopyTo(treeNode: ActorRef)
  case object CopyFinished

  def props(elem: Int, initiallyRemoved: Boolean) = Props(classOf[BinaryTreeNode],  elem, initiallyRemoved)
}

class BinaryTreeNode(val elem: Int, initiallyRemoved: Boolean) extends Actor with ActorLogging {
  import BinaryTreeNode._
  import BinaryTreeSet._

  var subtrees = Map[Position, ActorRef]()
  var removed = initiallyRemoved

  // optional
  def receive = LoggingReceive(normal)

  // optional
  /** Handles `Operation` messages and `CopyTo` requests. */
  val normal: Receive = {
    case CopyTo(node) => {
      context.become(copying(sender))
      //println(s"Received CopyTo in Main Node $self sender is $sender values ${subtrees.values.mkString(",")}")
      copy(CopyData(subtrees.values.toList,node,sender))
    }


    case msg@Insert(req,id,i) => {
      (subtrees.get(Left),subtrees.get(Right)) match {
        case _ if i == elem => { /*println(s"Inserted Equal $id");*/removed=false; req ! OperationFinished(id)}
        case (None,_) if i < elem => insert(Left ,i,id,req)
        case (_,None) if i > elem => insert(Right,i,id,req)
        case (Some(l),_) if i < elem => l ! msg
        case (_,Some(r)) if i > elem => r ! msg
      }
    }


    case msg@Contains(req,id,i) => {
      (removed,subtrees.get(Left),subtrees.get(Right)) match {
        case(_,_,_)            if i == elem      => req   ! ContainsResult(id,!removed)
        case(_,None,None)      if i != elem      => req   ! ContainsResult(id,false)
        case(_,_,Some(right))  if i > elem       => right ! msg
        case(_,Some(left),_)   if i < elem       => left  ! msg
        case _ => req ! ContainsResult(id,false)
      }
    }

    case msg@Remove(req,id,i) => {
     // println(s"Removing $i from $self value $elem subtrees ${subtrees.values.mkString}")
      (subtrees.get(Left),subtrees.get(Right)) match {
        case(_,_) if i == elem => {
          /*println(s"Found Removing $id")*/
          removed = true
          req ! OperationFinished(id)
        }
        case(None,None)       => {/*println("Not Found Leaf Removing");*/req ! OperationFinished(id)}
        case(_,Some(right))  if i > elem  => right ! msg
        case(Some(left),_) if i < elem    => left ! msg
        case _ => {/*println(s"Not Found Removing $id");*/req ! OperationFinished(id)}
      }
    }
  }

  override def unhandled(msg: Any) {
    log.debug("unhandled: {}", msg)
    super.unhandled(msg)
  }

  private def insert(pos: Position, i:Int , id:Int, req: ActorRef ) = {
    val newNode = context.actorOf(BinaryTreeNode.props(i, initiallyRemoved = false))
    subtrees = subtrees + (pos -> newNode)
/*    println(s"Inserting New Node $id")*/
    req ! OperationFinished(id)
  }


  def copying(origin: ActorRef) : Receive = {
    case msg@CopyFinished => {
      //println("Copy Finished")
      origin ! msg
    }
  }

  def copy(copyData: CopyData) : Unit = {
    val (actorOpt,cd) = copyData.next

  // println(s"\t\t actorOpt: $actorOpt")

    actorOpt match {
      case Some(actor) => actor.tell(CopyTo(cd.to),Once{
          case CopyFinished =>  {
           // println("Recursing Copy")
            copy(cd)
          }
      })

      case None => {
        if(removed){
    //     println(s"Sending Copy Finished from Removed Node ${context.self}")
          cd.origin ! CopyFinished
        }
        else {
          val once = Once{
            case OperationFinished(id) => {
        //     println(s"Sending CopyFinished to ${cd.origin}")
              cd.origin ! CopyFinished
            }
          }
      //  println("calling insert")
          copyData.to ! Insert(once,9999,elem)
        }
      }
    }
  }

  // optional
  /** `expected` is the set of ActorRefs whose replies we are waiting for,
    * `insertConfirmed` tracks whether the copy of this node to the new tree has been confirmed.
    */
  //def copying(expected: Set[ActorRef], insertConfirmed: Boolean): Receive = ???

  case class CopyData(actors: List[ActorRef], to: ActorRef, origin: ActorRef){
    def next = {
      actors match {
        case head :: Nil  => (Some(head), CopyData(Nil,to,origin))
        case head :: tail => (Some(head), CopyData(tail,to,origin))
        case Nil => (None,CopyData(Nil,to,origin))
      }
    }
  }
}

/*object IdGenerator{
  val gen = 0//new AtomicInteger(0)
  def next = 0// new Date().getTime()//gen.incrementAndGet()
}*/

class Once(handler: Receive) extends Actor {
  def receive = {
    case msg if handler.isDefinedAt(msg) => {
      handler(msg)
      context.stop(self)
    }
  }
}

object Once {
  def apply(handler: Receive)(implicit factory: ActorRefFactory) = {
    factory.actorOf(Props(new Once(handler)))
  }

}
