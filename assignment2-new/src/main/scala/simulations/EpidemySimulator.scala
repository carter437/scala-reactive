package simulations

import math.random

class EpidemySimulator extends Simulator {

  def randomBelow(i: Int) = (random * i).toInt

  protected[simulations] object SimConfig {
    val population: Int = 300
    val roomRows: Int = 8
    val roomColumns: Int = 8

    val prevelance = .01
    val transmissionRate = .4
    // to complete: additional parameters of simulation
  }

  import SimConfig._

  private def initialPopulation = {
    val infected = (population * prevelance).toInt

    //val vips = (1 to (population * .05).toInt).map(i => new Person(i,true)).toList

    val p = (1 to population).map(new Person(_)).toList
    p.take(infected).foreach(it => it.infect())
    p
  }

  val persons: List[Person] = initialPopulation

  class Person (val id: Int) {
    var infected = false
    var sick = false
    var immune = false
    var dead = false

    // demonstrates random number generation
    var row: Int = randomBelow(roomRows)
    var col: Int = randomBelow(roomColumns)

    var lastMoved = 0

    private def availableRooms = {
      def walk: Option[(Int,Int)] = {
        val left = if(row - 1  < 0) roomRows - 1 else row -1
        val right = if(row + 1 > roomRows - 1) 0 else row + 1

        val up =    if(col + 1  > roomColumns - 1) 0 else col + 1
        val down =  if(col - 1  < 0) roomColumns - 1 else col - 1

        val sickAndDead = persons.filterNot(_.id == id).filter(p => p.dead || p.sick)

        val rooms = List((left,col),(right,col),(row,up),(row,down))

        val avail = for{
          r <- rooms
          if !sickAndDead.exists(p => p.row == r._1 && p.col == r._2)
        } yield (r._1,r._2)

        avail match {
          case Nil => None
          case _ =>   Some(avail(randomBelow(avail.size)))
        }
      }

      def fly: Option[(Int,Int)] =  {
        Some((randomBelow(9),randomBelow(9)))
      }

    //if(randomBelow(100) == 0) fly else walk
    walk
    }

    private[simulations] def infect() {
      infected = true
      afterDelay(6){
        sick = true
        afterDelay(8){
          if(randomBelow(4) == 0) {
            immune = false
            sick = false
            dead = true
          }
          else afterDelay(2){
            if(!dead){
              sick = false
              immune = true
            }
            afterDelay(2) {
              if(!dead) {
                infected = false
                sick = false
                immune = false
              }
            }
          }
        }
      }
    }

    def expose(){
      if(!sick && !dead && !infected && !immune){
        if(persons.exists(p => p.row == row && p.col == col && (p.dead || p.infected || p.sick || p.immune ))){
          infect()
        }
      }
      else if(dead){
        sick = false
        immune = false
        infected = true
      }
    }

    def move() : Unit = {
      //println(s"=======> $id $lastMoved ${agenda.head.time}")
      afterDelay(randomBelow(5) + 1){ move() }
      //cd dval moveAllowed = true// if(sick) randomBelow(4) == 0 else randomBelow(2) == 0
      if(!dead){
        // println(s"Current Room $row,$col")
        availableRooms.map{  room =>
            row = room._1
            col = room._2
            // println(s"New Room $row,$col")
            expose()
          }
      }
      /*      assert(currentTime - lastMoved < 6, s"last moved $lastMoved time ${agenda.head.time}")
            lastMoved = currentTime*/
    }
    //
    // to complete with simulation logic

    //
  }

  private def ensureDead(p:Person){
    if(p.dead) {
      p.sick = false
      p.immune = false
    }
  }

  def go{
     persons.foreach(ensureDead)
     afterDelay(0){
        persons.foreach(_.move())
     }
  }

  go
}
