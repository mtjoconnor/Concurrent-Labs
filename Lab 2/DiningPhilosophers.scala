import ox.scl._
import scala.util.Random
import java.util.concurrent.atomic.AtomicInteger

/** Practical 2: Dining Philosophers variants.
  *
  * Required by the sheet: implement at least two variants.
  * This file implements all three variants.
  */
object DiningPhilosophers {
  sealed trait Variant
  case object RightHandedOne extends Variant
  case object Butler extends Variant
  case object Timeouts extends Variant

  final case class RunConfig(
      philosophers: Int = 5,
      mealsPerPhilosopher: Int = 30,
      timeoutMillis: Long = 40L,
      backoffMaxMillis: Int = 40,
      rightHandedId: Int = 0
  ) {
    require(philosophers >= 2)
    require(mealsPerPhilosopher >= 1)
    require(timeoutMillis >= 1L)
    require(backoffMaxMillis >= 1)
    require(rightHandedId >= 0 && rightHandedId < philosophers)
  }

  final case class RunResult(
      variant: Variant,
      config: RunConfig,
      mealsByPhilosopher: Vector[Int]
  ) {
    def complete: Boolean = mealsByPhilosopher.forall(_ == config.mealsPerPhilosopher)
    def totalMeals: Int = mealsByPhilosopher.sum
  }

  private def fork(id: Int, pick: ??[Unit], put: ??[Unit]): ThreadGroup =
    thread(s"Fork-$id") {
      var open = true
      while (open) {
        try {
          pick?()
          put?()
        } catch {
          case _: ox.scl.channel.Closed => open = false
        }
      }
    }

  private def closeForkChannels(picks: Vector[SyncChan[Unit]], puts: Vector[SyncChan[Unit]]): Unit = {
    picks.foreach(ch => try ch.close catch { case _: Throwable => () })
    puts.foreach(ch => try ch.close catch { case _: Throwable => () })
  }

  /** Run a variant to completion. */
  def runVariant(variant: Variant, config: RunConfig): RunResult = {
    val n = config.philosophers
    val picks = Vector.fill(n)(new SyncChan[Unit])
    val puts = Vector.fill(n)(new SyncChan[Unit])
    val done = new SyncChan[Int]
    val mealCounts = Array.fill(n)(new AtomicInteger(0))

    def leftFork(i: Int): Int = i
    def rightFork(i: Int): Int = (i + 1) % n

    def standardPhilosopher(i: Int, firstFork: Int, secondFork: Int, seat: !![Unit], leave: !![Unit]): ThreadGroup =
      thread(s"Philosopher-$i") {
        var k = 0
        while (k < config.mealsPerPhilosopher) {
          Thread.sleep(Random.nextInt(10) + 1)

          if (seat != null) seat ! ()
          picks(firstFork) ! ()
          picks(secondFork) ! ()

          mealCounts(i).incrementAndGet()
          k += 1

          puts(secondFork) ! ()
          puts(firstFork) ! ()
          if (leave != null) leave ! ()
        }
        done ! i
      }

    def timeoutPhilosopher(i: Int): ThreadGroup =
      thread(s"Philosopher-$i") {
        val l = leftFork(i)
        val r = rightFork(i)
        var k = 0
        while (k < config.mealsPerPhilosopher) {
          Thread.sleep(Random.nextInt(10) + 1)
          picks(l) ! ()
          val gotRight = picks(r).sendWithin(config.timeoutMillis)(())
          if (gotRight) {
            mealCounts(i).incrementAndGet()
            k += 1
            puts(r) ! ()
            puts(l) ! ()
          } else {
            // Timed out on second fork: release first fork and retry later.
            puts(l) ! ()
            Thread.sleep(Random.nextInt(config.backoffMaxMillis) + 1)
          }
        }
        done ! i
      }

    val forks = (0 until n).map(i => fork(i, picks(i), puts(i))).reduce(_ || _)

    val (philosophers, background, extraClosures) = variant match {
      case RightHandedOne =>
        val ps = (0 until n).map { i =>
          val l = leftFork(i)
          val r = rightFork(i)
          if (i == config.rightHandedId) standardPhilosopher(i, r, l, null, null)
          else standardPhilosopher(i, l, r, null, null)
        }.reduce(_ || _)
        (ps, thread("NoBackground") {}, Vector.empty[SyncChan[Unit]])

      case Butler =>
        val enter = new SyncChan[Unit]
        val leave = new SyncChan[Unit]
        val butler = thread("Butler") {
          var seated = 0
          var open = true
          while (open) {
            try {
              alt(
                (seated < n - 1) && (enter =?=> { _ => seated += 1 }) |
                (seated > 0) && (leave =?=> { _ => seated -= 1 })
              )
            } catch {
              case _: ox.scl.channel.Closed => open = false
              case _: ox.scl.channel.AltAbort => open = false
            }
          }
        }
        val ps = (0 until n)
          .map(i => standardPhilosopher(i, leftFork(i), rightFork(i), enter, leave))
          .reduce(_ || _)
        (ps, butler, Vector(enter, leave))

      case Timeouts =>
        val ps = (0 until n).map(timeoutPhilosopher).reduce(_ || _)
        (ps, thread("NoBackground") {}, Vector.empty[SyncChan[Unit]])
    }

    val closer = thread("Closer") {
      for (_ <- 0 until n) done?()
      done.close
      closeForkChannels(picks, puts)
      extraClosures.foreach(ch => try ch.close catch { case _: Throwable => () })
    }

    run(forks || philosophers || background || closer)

    RunResult(
      variant = variant,
      config = config,
      mealsByPhilosopher = mealCounts.map(_.get).toVector
    )
  }
}

/** Tests for Practical 2 variants. */
object DiningPhilosophersTest {
  import DiningPhilosophers._

  private def assertCompleted(result: RunResult): Unit = {
    assert(
      result.complete,
      s"${result.variant} did not complete: ${result.mealsByPhilosopher.mkString("[", ", ", "]")}"
    )
  }

  def main(args: Array[String]): Unit = {
    val config = RunConfig(
      philosophers = if (args.nonEmpty) args(0).toInt else 5,
      mealsPerPhilosopher = if (args.length > 1) args(1).toInt else 20
    )

    val rightHanded = runVariant(RightHandedOne, config)
    assertCompleted(rightHanded)

    val butler = runVariant(Butler, config)
    assertCompleted(butler)

    val timeout = runVariant(Timeouts, config)
    assertCompleted(timeout)

    println(s"Lab2 tests passed for n=${config.philosophers}, meals=${config.mealsPerPhilosopher}")
    println(s"RightHandedOne: ${rightHanded.mealsByPhilosopher.mkString(",")}")
    println(s"Butler: ${butler.mealsByPhilosopher.mkString(",")}")
    println(s"Timeouts: ${timeout.mealsByPhilosopher.mkString(",")}")
  }
}
