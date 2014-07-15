package simulations

import org.scalatest.FunSuite

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class CircuitSuite extends CircuitSimulator with FunSuite {
  val InverterDelay = 1
  val AndGateDelay = 3
  val OrGateDelay = 5

  test("demux 0 channel"){

    val in,out = new Wire()

    demux(in,Nil,List(out))

    in.setSignal(true)
    run
    assert(out.getSignal)
  }


  test("demux 1 channel"){
    val channels = 1
    val in = new Wire()
    val control = (1 to channels).map(i => new Wire).toList
    val outs     = (1 to math.pow(2,channels).toInt).map(i => new Wire).toList

    demux(in,control,outs)
    control(0).setSignal(true)
    in.setSignal(true)
    run
    assert(outs(0).getSignal)
  }


  test("demux 2 channel"){

    val channels = 2
    val in = new Wire()

    val truthTable = Map(
    List(false,false,false) -> List(false,false,false,false),
    List(false,false,true)  -> List(false,false,false,true),

    List(false,true,false)  -> List(false,false,false,false),
    List(false,true,true)   -> List(false,false,true,false),

    List(true,false,false)  -> List(false,false,false,false),
    List(true,false,true)   -> List(false,true,false,false),

    List(true,true,true)    -> List(false,false,false,false),
    List(true,true,true)    -> List(true,false,false,false)
    )


    truthTable.foreach{ case (k,v) =>
       val inVal = k.reverse.head
       val controlvals = k.reverse.tail.reverse
       val controls = controlvals.map{ it =>
          val w = new Wire()
          w
       }
      val outs     = (1 to math.pow(2,channels).toInt).map(i => new Wire).toList
      demux(in,controls,outs)
       controls.zip(k).foreach( it => it._1.setSignal(it._2))
       in.setSignal(inVal)
       run
       assert(outs.map(_.getSignal) == v)
    }
  }
  //
  // to complete with tests for orGate, demux, ...
  //

  test("demux 0 channel"){

    val in,out = new Wire()

    demux(in,Nil,List(out))

    in.setSignal(true)
    run
    assert(out.getSignal)
  }


  test("demux 1 channel"){
    val channels = 1
    val in = new Wire()
    val control = (1 to channels).map(i => new Wire).toList
    val outs     = (1 to math.pow(2,channels).toInt).map(i => new Wire).toList

    demux(in,control,outs)
    control(0).setSignal(true)
    in.setSignal(true)
    run
    assert(outs(0).getSignal)
  }


  test("demux 2 channel"){

    val channels = 2
    val in = new Wire()

    val truthTable = Map(
      List(false,false,false) -> List(false,false,false,false),
      List(false,false,true)  -> List(false,false,false,true),

      List(false,true,false)  -> List(false,false,false,false),
      List(false,true,true)   -> List(false,false,true,false),

      List(true,false,false)  -> List(false,false,false,false),
      List(true,false,true)   -> List(false,true,false,false),

      List(true,true,true)    -> List(false,false,false,false),
      List(true,true,true)    -> List(true,false,false,false)
    )


    truthTable.foreach{ case (k,v) =>
      val inVal = k.reverse.head
      val controlvals = k.reverse.tail.reverse
      val controls = controlvals.map{ it =>
        val w = new Wire()
        w
      }
      val outs     = (1 to math.pow(2,channels).toInt).map(i => new Wire).toList
      demux(in,controls,outs)
      controls.zip(k).foreach( it => it._1.setSignal(it._2))
      in.setSignal(inVal)
      run
      assert(outs.map(_.getSignal) == v)
    }
  }
}
