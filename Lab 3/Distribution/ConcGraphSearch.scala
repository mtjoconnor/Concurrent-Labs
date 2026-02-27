import ox.scl._
import scala.collection.mutable.Stack

class ConcGraphSearch[N](g: Graph[N]) extends GraphSearch[N](g){
  /**The number of workers. */
  val numWorkers = 8

  /** Shared work stack with termination tracking. */
  private class SharedState {
    private val stack = new Stack[N]
    private var pending = 0 // work items in stack or currently being processed
    private var result: Option[N] = None

    def init(start: N): Unit = synchronized {
      stack.push(start)
      pending = 1
      result = None
    }

    /** Obtain next node, blocking while work may still arrive. */
    def takeWork(): Option[N] = synchronized {
      while (result.isEmpty && stack.isEmpty && pending > 0) wait()
      if (result.nonEmpty) None
      else if (stack.nonEmpty) Some(stack.pop())
      else None // pending == 0 and no result
    }

    /** Report that node is a solution. */
    def foundSolution(node: N): Unit = synchronized {
      if (result.isEmpty) result = Some(node)
      pending -= 1
      notifyAll()
    }

    /** Report node completion, optionally adding successors. */
    def completeNode(succs: List[N]): Unit = synchronized {
      if (result.isEmpty) {
        succs.foreach(stack.push)
        pending += succs.length
      }
      pending -= 1
      notifyAll()
    }

    def getResult: Option[N] = synchronized { result }
  }

  /** Perform a depth-first search in g, starting from start, for a node that
    * satisfies isTarget. */
  def apply(start: N, isTarget: N => Boolean): Option[N] = {
    val state = new SharedState
    state.init(start)

    def worker(me: Int) = thread("DFSWorker"+me){
      var continue = true
      while (continue) {
        state.takeWork() match {
          case None => continue = false
          case Some(n) =>
            if (isTarget(n)) state.foundSolution(n)
            else state.completeNode(g.succs(n))
        }
      }
    }

    run(||((0 until numWorkers).map(worker)))
    state.getResult
  }
}
