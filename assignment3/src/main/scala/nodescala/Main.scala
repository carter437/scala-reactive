package nodescala

import scala.language.postfixOps
import scala.concurrent._
import scala.concurrent.duration._
import ExecutionContext.Implicits.global
import scala.async.Async.{async, await}
import scala.util.{Failure, Success, Try}

object Main {

  def main(args: Array[String]) {
    // TO IMPLEMENT
    // 1. instantiate the server at 8191, relative path "/test",
    //    and have the response return headers of the request
    val myServer = new NodeScala.Default(8191)
    val myServerSubscription = myServer.start("/test"){
      request => for (kv <- request.iterator) yield kv + "\n"
    }

    // TO IMPLEMENT
    // 2. create a future that expects some user input `x`
    //    and continues with a `"You entered... " + x` message
    val userInterrupted: Future[String] = Future.userInput("Enter Message:").map{ s =>
      s"You entered $s"
    }

    // TO IMPLEMENT
    // 3. create a future that completes after 20 seconds
    //    and continues with a `"Server timeout!"` message
    val timeOut: Future[String] = {
      val p = Promise[String]()

          future{
              try{
              Await.ready(Future.never[String], 20 seconds)
              throw new RuntimeException("Should not have completed")
              }
              catch{
                case t:Throwable => p.complete(Try("Server timeout!"))
              }
          }
      p.future
    }

/*
 val result = Try(await{ Future.never[String]}).recover{
          case t:Throwable => "Server timeout!"
        }
        p.complete(result)*/

    // TO IMPLEMENT
    // 4. create a future that completes when either 10 seconds elapse
    //    or the user enters some text and presses ENTER
    val terminationRequested: Future[String] = {
      val f1 = Future.delay(10 seconds).map(_ => "Completed after 10 seconds")

      Future.any(List(f1,userInterrupted, timeOut))
    }

    // TO IMPLEMENT
    // 5. unsubscribe from the server
    terminationRequested onSuccess {
      case msg => {
        println("Success, Unsubscribing")
        myServerSubscription.unsubscribe()
      }
    }

    terminationRequested onFailure {
      case x => println(s"Failure: $x")
    }
  }

}