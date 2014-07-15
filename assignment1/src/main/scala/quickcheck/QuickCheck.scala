package quickcheck

import common._

import org.scalacheck._
import Arbitrary._
import Gen._
import Prop._

abstract class QuickCheckHeap extends Properties("Heap") with IntHeap {

  property("constantly sorted") = forAll { ints:List[Int] =>
    def recurse(h1: H) : Seq[A] = {
      if(isEmpty(h1))
        Nil
      else {
        Seq(findMin(h1)) ++ recurse(deleteMin(h1))
      }
    }
    val sorted = ints.sorted
    val heap = ints.foldLeft(empty)((h,i) => insert(i,h))

    recurse(heap).sorted == sorted
  }

/*  property("min1") = forAll { a: Int =>
    val h = insert(a, empty)
    findMin(h) == a
  }*/

  property("min2") = forAll { (a: Int, b:Int) =>
    val h = meld(insert(a,empty),insert(b,empty))
    findMin(h) == math.min(a,b)
  }


/*  property("delete min1") = forAll { h: H =>
    lazy val assertion = {
      val min = findMin(h)
      val deleted = deleteMin(h)
      if(isEmpty(deleted)){
        true
      }
      else {
        ord.gteq(findMin(deleted),min)
      }
    }

    if(isEmpty(h)){
      Prop.throws(classOf[NoSuchElementException]){ assertion }
    }
    else
      assertion
  }*/

/*  property("delete then insert") = forAll { h: H =>
    !isEmpty(h) ==> {
      val min = findMin(h)
      min == findMin(insert(min,deleteMin(h)))
    }
  }*/

/*  property("insert then delete") = forAll { (h1: H, h2: H) =>
    (!isEmpty(h1) && !isEmpty(h2)) ==> {
      val min1 = findMin(h1)
      val min2 = findMin(h2)

      if(ord.lt(min2,min1)){
        findMin(deleteMin(insert(min2,h1))) == min1
      } else {
        findMin(deleteMin(insert(min1,h2))) == min2
      }
    }
  }*/


/*  property("min of two heaps") = forAll{ (h1: H, h2: H) =>

    lazy val minH1 = findMin(h1)
    lazy val minH2 = findMin(h2)
    lazy val minOfBoth =  ord.min(minH1,minH2)
    lazy val minOfMeld =  findMin(meld(h1,h2))
    lazy val assertion = minOfBoth == minOfMeld

    if(isEmpty(h1) || isEmpty(h2))
      Prop.throws(classOf[NoSuchElementException]){ assertion }
    else
      assertion
  }*/
/*
  property("meld left empty") = forAll{ (h1: H) =>
       meld(h1,empty) == h1
  }*/

/*  property("meld right empty") = forAll{ (h1: H) =>
    meld(empty,h1) == h1
  }*/





  lazy val genHeap: Gen[H] = {
    for{
      i <- arbitrary[Int]
      h <- oneOf(value(empty),genHeap)
    } yield insert(i,h)
  }

  implicit lazy val arbHeap: Arbitrary[H] = Arbitrary(genHeap)

}
