package simulations

import common._

class Wire {
  private var sigVal = false
  private var actions: List[Simulator#Action] = List()

  def getSignal: Boolean = sigVal
  
  def setSignal(s: Boolean) {
    if (s != sigVal) {
      sigVal = s
      actions.foreach(action => action())
    }
  }

  def addAction(a: Simulator#Action) {
    actions = a :: actions
    a()
  }
}

abstract class CircuitSimulator extends Simulator {

  val InverterDelay: Int
  val AndGateDelay: Int
  val OrGateDelay: Int

  def probe(name: String, wire: Wire) {
    wire addAction {
      () => afterDelay(0) {
        println(
          "  " + currentTime + ": " + name + " -> " +  wire.getSignal)
      }
    }
  }

  def inverter(input: Wire, output: Wire) {
    def invertAction() {
      val inputSig = input.getSignal
      afterDelay(InverterDelay) { output.setSignal(!inputSig) }
    }
    input addAction invertAction
  }

  def andGate(a1: Wire, a2: Wire, output: Wire) {
    def andAction() {
      val a1Sig = a1.getSignal
      val a2Sig = a2.getSignal
      afterDelay(AndGateDelay) { output.setSignal(a1Sig & a2Sig) }
    }
    a1 addAction andAction
    a2 addAction andAction
  }

  //
  // to complete with orGates and demux...
  //

  def orGate(a1: Wire, a2: Wire, output: Wire) {
    def orAction() {
      val a1Sig = a1.getSignal
      val a2Sig = a2.getSignal
      afterDelay(OrGateDelay) { output.setSignal(a1Sig | a2Sig) }
    }
    a1 addAction orAction
    a2 addAction orAction
  }
  
  def orGate2(a1: Wire, a2: Wire, output: Wire) {
    val w1,w2,w3 = new Wire
    inverter(a1,w1)
    inverter(a2,w2)
    andGate(w1,w2,w3)
    inverter(w3,output)
  }

  def demux(in:Wire, c:List[Wire], out: List[Wire]) = {
    def build(in: Wire, c: Wire) : Wire = {
      val o = new Wire()
      andGate(in,c,o)
      o
    }

    def decode(in: Wire, c:List[Wire]) : List[Wire] = {
      c match {
        case Nil => List(in)
        case head::tail => {
          decode(build(in,invert(head)),tail) ::: decode(build(in,head),tail)
        }
      }
    }

    def invert(in:Wire) : Wire = {
      val out = new Wire()
      inverter(in,out)
      out
    }

    decode(in,c).zip(out.reverse).map( w => andGate(w._1,in,w._2))
  }
}

object Circuit extends CircuitSimulator {
  var i = 0
  var j = 0
  val InverterDelay = 1
  val AndGateDelay = 3
  val OrGateDelay = 5

  def andGateExample {
    val in1, in2, out = new Wire
    andGate(in1, in2, out)
    probe("in1", in1)
    probe("in2", in2)
    probe("out", out)
    in1.setSignal(false)
    in2.setSignal(false)
    run

    in1.setSignal(true)
    run

    in2.setSignal(true)
    run
  }


  def zeroChanneldemuxExample {
    val in,out = new Wire()
    demux(in,Nil,List(out))
    probe("out",out)

    in.setSignal(true)
    run

  }
  //
  // to complete with orGateExample and demuxExample...
  //
}

object CircuitMain extends App {
  // You can write tests either here, or better in the test class CircuitSuite.
 // Circuit.andGateExample
}
