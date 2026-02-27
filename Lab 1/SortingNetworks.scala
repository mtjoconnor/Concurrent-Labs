import ox.scl._
import scala.util.Random

/** Practical 1: Sorting Networks.
  *
  * This file implements required Questions 1, 2, 3 and 5.
  * Optional Questions 4, 6 and 7 are documented in report notes.
  */
object SortingNetworks {
  /** A single comparator, inputting on in0 and in1, and outputting on out0
    * (smaller value) and out1 (larger value). */
  def comparator(in0: ??[Int], in1: ??[Int], out0: !![Int], out1: !![Int]): ThreadGroup =
    thread("comparator") {
      val pollMillis = 5L

      def receivePair(): Option[(Int, Int)] = {
        var x0: Option[Int] = None
        var x1: Option[Int] = None
        while (x0.isEmpty || x1.isEmpty) {
          if (x0.isEmpty) {
            x0 = in0.receiveWithin(pollMillis)
            if (x0.isEmpty && in0.isClosed) return None
          }
          if (x1.isEmpty) {
            x1 = in1.receiveWithin(pollMillis)
            if (x1.isEmpty && in1.isClosed) return None
          }
        }
        Some((x0.get, x1.get))
      }

      def sendBoth(lo: Int, hi: Int): Unit = {
        var sentLo = false
        var sentHi = false
        while (!sentLo || !sentHi) {
          if (!sentLo) sentLo = out0.sendWithin(pollMillis)(lo)
          if (!sentHi) sentHi = out1.sendWithin(pollMillis)(hi)
        }
      }

      var keepGoing = true
      while (keepGoing) {
        try {
          receivePair() match {
            case None => keepGoing = false
            case Some((x0, x1)) =>
              val lo = math.min(x0, x1)
              val hi = math.max(x0, x1)
              sendBoth(lo, hi)
          }
        } catch {
          case _: ox.scl.channel.Closed => keepGoing = false
        }
      }

      // Propagate termination so downstream comparators do not block forever.
      try out0.endOfStream
      catch { case _: ox.scl.channel.Closed => () }
      try out1.endOfStream
      catch { case _: ox.scl.channel.Closed => () }
    }

  /** A sorting network for four values. */
  def sort4(ins: List[??[Int]], outs: List[!![Int]]): ThreadGroup = {
    require(ins.length == 4 && outs.length == 4)

    val s0 = new SyncChan[Int]
    val s1 = new SyncChan[Int]
    val s2 = new SyncChan[Int]
    val s3 = new SyncChan[Int]
    val t1 = new SyncChan[Int]
    val t2 = new SyncChan[Int]

    comparator(ins(0), ins(1), s0, s1) ||
    comparator(ins(2), ins(3), s2, s3) ||
    comparator(s0, s2, outs(0), t2) ||
    comparator(s1, s3, t1, outs(3)) ||
    comparator(t1, t2, outs(1), outs(2))
  }

  /** Insert a value input on in into a sorted sequence input on ins.
    * Pre: ins.length = n && outs.length = n+1, for some n >= 1.
    * If the values xs input on ins are sorted, and x is input on in, then a
    * sorted permutation of x::xs is output on ys. */
  def insert(ins: List[??[Int]], in: ??[Int], outs: List[!![Int]]): ThreadGroup = {
    val n = ins.length
    require(n >= 1 && outs.length == n + 1)

    if (n == 1) comparator(ins.head, in, outs.head, outs(1))
    else {
      val restIn = new SyncChan[Int]
      comparator(ins.head, in, outs.head, restIn) ||
      insert(ins.tail, restIn, outs.tail)
    }
  }

  /** Insertion sort. */
  def insertionSort(ins: List[??[Int]], outs: List[!![Int]]): ThreadGroup = {
    val n = ins.length
    require(n >= 2 && outs.length == n)

    if (n == 2) comparator(ins(0), ins(1), outs(0), outs(1))
    else {
      val sortedTail = List.fill(n - 1)(new SyncChan[Int])
      insertionSort(ins.tail, sortedTail) ||
      insert(sortedTail, ins.head, outs)
    }
  }
}

/** Test harness for Practical 1 required questions. */
object SortingNetworksTest {
  import SortingNetworks._

  private def isSorted(xs: List[Int]): Boolean =
    xs.zip(xs.drop(1)).forall { case (x, y) => x <= y }

  private def sameMultiset(xs: List[Int], ys: List[Int]): Boolean =
    xs.sorted == ys.sorted

  private def runSort4Once(values: List[Int]): List[Int] = {
    require(values.length == 4)
    val ins = List.fill(4)(new SyncChan[Int])
    val outs = List.fill(4)(new SyncChan[Int])
    val result = Array.ofDim[Int](4)

    val sender = thread("sort4-sender") {
      for ((x, ch) <- values.zip(ins)) ch ! x
      ins.foreach(_.endOfStream)
    }
    val receiver = thread("sort4-receiver") {
      for (i <- 0 until 4) result(i) = outs(i)?()
    }

    run(sort4(ins, outs) || sender || receiver)
    result.toList
  }

  private def runInsertOnce(sortedIns: List[Int], x: Int): List[Int] = {
    val n = sortedIns.length
    require(n >= 1)

    val ins = List.fill(n)(new SyncChan[Int])
    val in = new SyncChan[Int]
    val outs = List.fill(n + 1)(new SyncChan[Int])
    val result = Array.ofDim[Int](n + 1)

    val senderA = thread("insert-sender-ins") {
      for ((v, ch) <- sortedIns.zip(ins)) ch ! v
      ins.foreach(_.endOfStream)
    }
    val senderB = thread("insert-sender-in") {
      in ! x
      in.endOfStream
    }
    val receiver = thread("insert-receiver") {
      for (i <- 0 to n) result(i) = outs(i)?()
    }

    run(insert(ins, in, outs) || senderA || senderB || receiver)
    result.toList
  }

  private def runInsertionSortOnce(values: List[Int]): List[Int] = {
    val n = values.length
    require(n >= 2)

    val ins = List.fill(n)(new SyncChan[Int])
    val outs = List.fill(n)(new SyncChan[Int])
    val result = Array.ofDim[Int](n)

    val sender = thread("isort-sender") {
      for ((v, ch) <- values.zip(ins)) ch ! v
      ins.foreach(_.endOfStream)
    }
    val receiver = thread("isort-receiver") {
      for (i <- 0 until n) result(i) = outs(i)?()
    }

    run(insertionSort(ins, outs) || sender || receiver)
    result.toList
  }

  def testSort4(trials: Int): Unit = {
    for (_ <- 0 until trials) {
      val xs = List.fill(4)(Random.nextInt(2001) - 1000)
      val ys = runSort4Once(xs)
      assert(isSorted(ys), s"sort4 output not sorted: in=$xs out=$ys")
      assert(sameMultiset(xs, ys), s"sort4 output not permutation: in=$xs out=$ys")
    }
  }

  def testInsert(trials: Int): Unit = {
    for (_ <- 0 until trials) {
      val n = Random.between(1, 9)
      val xs = List.fill(n)(Random.nextInt(201) - 100).sorted
      val x = Random.nextInt(201) - 100
      val ys = runInsertOnce(xs, x)
      assert(isSorted(ys), s"insert output not sorted: xs=$xs x=$x out=$ys")
      assert(sameMultiset(x :: xs, ys), s"insert output not permutation: xs=$xs x=$x out=$ys")
    }
  }

  def testInsertionSort(trials: Int): Unit = {
    for (_ <- 0 until trials) {
      val n = Random.between(2, 11)
      val xs = List.fill(n)(Random.nextInt(2001) - 1000)
      val ys = runInsertionSortOnce(xs)
      assert(isSorted(ys), s"insertionSort output not sorted: in=$xs out=$ys")
      assert(sameMultiset(xs, ys), s"insertionSort output not permutation: in=$xs out=$ys")
    }
  }

  def main(args: Array[String]): Unit = {
    val sort4Trials = if (args.nonEmpty) args(0).toInt else 300
    val insertTrials = if (args.length > 1) args(1).toInt else 300
    val isortTrials = if (args.length > 2) args(2).toInt else 300

    testSort4(sort4Trials)
    testInsert(insertTrials)
    testInsertionSort(isortTrials)
    println(
      s"Lab1 tests passed: sort4=$sort4Trials, insert=$insertTrials, insertionSort=$isortTrials"
    )
  }
}
