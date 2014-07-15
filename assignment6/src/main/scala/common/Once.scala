package common

import scala.concurrent.duration.Duration
import akka.actor.Actor._
import akka.actor.{Props, ActorRefFactory, Actor}

class Once(timeout: Duration, handler: Receive) extends Actor {

  context.setReceiveTimeout(timeout)

  def receive = {
    case msg if handler.isDefinedAt(msg) => {
      handler(msg)
      context.stop(self)
    }
  }
}

object Once {
  def apply(handler: Receive)(implicit factory: ActorRefFactory) = {
    factory.actorOf(Props(new Once(Duration.Undefined, handler)))
  }
  def withTimeout(timeout: Duration)(handler: Receive)(implicit factory: ActorRefFactory) = {
    factory.actorOf(Props(new Once(timeout, handler)))
  }
}