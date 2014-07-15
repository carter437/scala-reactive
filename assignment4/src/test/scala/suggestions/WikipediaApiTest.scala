package suggestions



import language.postfixOps
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Try, Success, Failure}
import rx.lang.scala._
import org.scalatest._
import gui._

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import rx.lang.scala.concurrency.Schedulers


@RunWith(classOf[JUnitRunner])
class WikipediaApiTest extends FunSuite {

  object mockApi extends WikipediaApi {
    def wikipediaSuggestion(term: String) = Future {
      if (term.head.isLetter) {
        for (suffix <- List(" (Computer Scientist)", " (Footballer)")) yield term + suffix
      } else {
        List(term)
      }
    }
    def wikipediaPage(term: String) = Future {
      "Title: " + term
    }
  }

  import mockApi._

  test("WikipediaApi should make the stream valid using sanitized") {
    val notvalid = Observable("erik", "erik meijer", "martin")
    val valid = notvalid.sanitized

    var count = 0
    var completed = false

    val sub = valid.subscribe(
      term => {
        assert(term.forall(_ != ' '))
        count += 1
      },
      t => assert(false, s"stream error $t"),
      () => completed = true
    )
    assert(completed && count == 3, "completed: " + completed + ", event count: " + count)
  }

  test( "recovered from map" ) {
    val req = Observable( 1, 2, 3 )
    val response = req.map( num ⇒ if ( num != 2 ) num else new Exception( "MyException" ) ).recovered
    val l = response.toBlockingObservable.toList
    println(l)
  }

  test( "recovered from flatMap" ) {
    val req = Observable( 1, 2, 3 )
    val response = req.flatMap( num ⇒ if ( num != 2 ) Observable( num ) else Observable( new Exception( "MyException" ) ) ).recovered
    val l = response.toBlockingObservable.toList
    println(l)
  }


  test("recovered 2") {
    val ex1 = new Exception("!")
    val o1 = Observable(1,2)
    val o2 = Observable(ex1)
    val o3 = Observable(4,5)
    val xs = (o1 ++ o2 ++ o3).recovered.toBlockingObservable.toList
    assert(xs === List(Success(1),Success(2),Failure(ex1)))
  }

  test("concatRecovered 2") {
    val ex2 = new Exception("?")
    def dummy(num: Int) = {
      if (num != 4) Observable(num) else Observable(ex2)
    }
    val o1 = Observable(1,2,3,4,5).concatRecovered(x => dummy(x))
    val xs = o1.toBlockingObservable.toList
    assert(xs === List(Success(1),Success(2),Success(3),Failure(ex2),Success(5)))
  }

  test("concatRecovered test with delays to test correct ordering") {
    val requests = Observable(3, 2, 1)
    val remoteComputation = (num: Int) => Observable.interval(num seconds).map(_ => num).take(2)
    val responses = requests concatRecovered remoteComputation
    val result = responses.toBlockingObservable.toList
    assert(result == List(Success(3), Success(3),
      Success(2), Success(2),
      Success(1), Success(1)))
  }


  test( "recovered from map 1" ) {
    val req = Observable( 1, 2, 3 )
    val response: Observable[ Try[ Any ] ] = req.map( num ⇒ if ( num != 2 ) num else { new Exception( "MyException" ) } ).recovered
    println("-->" + response.toBlockingObservable.toList )
  }

  test( "recovered from map 2" ) {
    val req = Observable( 1, 2, 3 )
    val response: Observable[ Try[ Int ] ] = req.map( num ⇒ if ( num != 2 ) num else { throw new Exception( "MyException" ); num } ).recovered
    println("xxx>" + response.toBlockingObservable.toList )
  }

  test("concatRecovered behaves as promised") {
    val req = Observable(1, 2, 3, 4, 5)
    val response = req.concatRecovered(num => if (num != 4) Observable(num) else Observable(new Exception))

    val res = response.foldLeft((0, 0)) {
      (acc, tn) =>
        println(tn)
        tn match {
          case Success(n) => (acc._1 + n, acc._2)
          case Failure(_) => (acc._1, acc._2 + 1)
        }
    }

    var pair = (0, 0)
    res.observeOn(Schedulers.immediate).subscribe(e => pair = e)
    val (sum, fc) = pair
    assert(sum == (1 + 2 + 3 + 5), "Wrong sum: " + sum)
    assert(fc == 1, "Wrong failurecount: " + fc)
  }


  ignore("WikipediaApi should correctly use concatRecovered") {
    val requests = Observable(1, 2, 3)
    val remoteComputation = (n: Int) => Observable(0 to n)
    val responses = requests concatRecovered remoteComputation
    val sum = responses.foldLeft(0) { (acc, tn) =>
     // println(tn)
      tn match {
        case Success(n) => acc + n
        case Failure(t) => throw t
      }
    }
    var total = -1
    val sub = sum.subscribe {
      s => total = s
    }
    assert(total == (1 + 1 + 2 + 1 + 2 + 3), s"Sum: $total")
  }
}