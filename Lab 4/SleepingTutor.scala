// Template for the Sleeping Tutor practical

import ox.scl._

/** The trait for a Sleeping Tutor protocol. */
trait SleepingTutor{
  /** A tutor waits for students to arrive. */
  def tutorWait: Unit

  /** A student arrives and waits for the tutorial. */
  def arrive: Unit
  
  /** A student receives a tutorial. */
  def receiveTute: Unit

  /** A tutor ends the tutorial. */
  def endTeach: Unit
}

// =======================================================

/** Monitor-based implementation. */
class SleepingTutorMonitor extends SleepingTutor{
  private object Phase extends Enumeration{
    val WaitingForStudents, Teaching, TeachingEnded = Value
  }

  private var phase = Phase.WaitingForStudents
  private var arrived = 0
  private var left = 0

  def tutorWait: Unit = synchronized {
    while(phase != Phase.Teaching) wait()
  }

  def arrive: Unit = synchronized {
    while(phase != Phase.WaitingForStudents || arrived == 2) wait()
    arrived += 1
    if(arrived == 2){
      phase = Phase.Teaching
      notifyAll()
    }else{
      // First student waits until second student arrives.
      while(phase == Phase.WaitingForStudents) wait()
    }
  }

  def receiveTute: Unit = synchronized {
    while(phase != Phase.TeachingEnded) wait()
    left += 1
    if(left == 2){
      // Reset for next tutorial cycle.
      left = 0
      arrived = 0
      phase = Phase.WaitingForStudents
      notifyAll()
    }
  }

  def endTeach: Unit = synchronized {
    phase = Phase.TeachingEnded
    notifyAll()
  }
}

/** Semaphore-based implementation (optional extension). */
class SleepingTutorSemaphore extends SleepingTutor{
  private val gate = new java.util.concurrent.Semaphore(2)
  private val mutex = new java.util.concurrent.Semaphore(1)
  private val wakePartner = new java.util.concurrent.Semaphore(0)
  private val wakeTutor = new java.util.concurrent.Semaphore(0)
  private val tuteDone = new java.util.concurrent.Semaphore(0)

  private var arrived = 0
  private var left = 0

  def tutorWait: Unit = wakeTutor.acquire()

  def arrive: Unit = {
    gate.acquire()
    var iAmFirst = false
    mutex.acquire()
    arrived += 1
    if(arrived == 1) iAmFirst = true
    else{
      // Second student wakes first student and tutor.
      wakePartner.release()
      wakeTutor.release()
    }
    mutex.release()
    if(iAmFirst) wakePartner.acquire()
  }

  def receiveTute: Unit = {
    tuteDone.acquire()
    var iAmLastToLeave = false
    mutex.acquire()
    left += 1
    if(left == 2){
      left = 0
      arrived = 0
      iAmLastToLeave = true
    }
    mutex.release()
    if(iAmLastToLeave) gate.release(2)
  }

  def endTeach: Unit = tuteDone.release(2)
}

// =======================================================

import scala.util.Random

object SleepingTutorSimulation{
  // Some implementation of SleepingTutor
  private val st: SleepingTutor = new SleepingTutorMonitor

  private def student(me: String) = thread("Student"+me){
    while(true){
      Thread.sleep(Random.nextInt(2000))
      println("Student "+me+" arrives")
      st.arrive
      println("Student "+me+" ready for tutorial")
      st.receiveTute
      println("Student "+me+" leaves")
    }
  }

  private def tutor = thread("Tutor"){
    while(true){
      println("Tutor waiting for students")
      st.tutorWait
      println("Tutor starts to teach")
      Thread.sleep(1000)
      println("Tutor ends tutorial")
      st.endTeach
      Thread.sleep(1000)
    }
  }

  def system = tutor || student("Alice") || student("Bob")

  def main(args: Array[String]) = run(system)
}

// =======================================================

/** Tests for SleepingTutor protocol requirements. */
object SleepingTutorTest{
  private def runScenario(factory: () => SleepingTutor, cycles: Int): Vector[String] = {
    val st = factory()
    val events = collection.mutable.ArrayBuffer[String]()
    def log(e: String): Unit = events.synchronized { events += e }

    val alice = new Thread(() => {
      for(c <- 0 until cycles){
        log(s"A-arrive-$c")
        st.arrive
        log(s"A-ready-$c")
        st.receiveTute
        log(s"A-leave-$c")
      }
    })

    val bob = new Thread(() => {
      for(c <- 0 until cycles){
        log(s"B-arrive-$c")
        st.arrive
        log(s"B-ready-$c")
        st.receiveTute
        log(s"B-leave-$c")
      }
    })

    val tutor = new Thread(() => {
      for(c <- 0 until cycles){
        log(s"T-wait-$c")
        st.tutorWait
        log(s"T-start-$c")
        Thread.sleep(5)
        log(s"T-end-$c")
        st.endTeach
      }
    })

    tutor.start(); alice.start(); bob.start()
    tutor.join(); alice.join(); bob.join()
    events.toVector
  }

  private def indexOf(events: Vector[String], name: String): Int = {
    val i = events.indexOf(name)
    require(i >= 0, s"missing event $name in ${events.mkString(", ")}")
    i
  }

  private def assertRequirements(events: Vector[String], cycles: Int): Unit = {
    for(c <- 0 until cycles){
      val aArrive = indexOf(events, s"A-arrive-$c")
      val bArrive = indexOf(events, s"B-arrive-$c")
      val tStart = indexOf(events, s"T-start-$c")
      val tEnd = indexOf(events, s"T-end-$c")
      val aLeave = indexOf(events, s"A-leave-$c")
      val bLeave = indexOf(events, s"B-leave-$c")

      assert(tStart > aArrive && tStart > bArrive,
        s"Requirement 1 failed in cycle $c: tutor started too early")
      assert(aLeave > tEnd && bLeave > tEnd,
        s"Requirement 2 failed in cycle $c: student left too early")
    }
  }

  def main(args: Array[String]): Unit = {
    val cycles = if(args.nonEmpty) args(0).toInt else 30

    val monitorEvents = runScenario(() => new SleepingTutorMonitor, cycles)
    assertRequirements(monitorEvents, cycles)

    val semaphoreEvents = runScenario(() => new SleepingTutorSemaphore, cycles)
    assertRequirements(semaphoreEvents, cycles)

    println(s"Lab4 tests passed for $cycles cycles (monitor + semaphore)")
  }
}
