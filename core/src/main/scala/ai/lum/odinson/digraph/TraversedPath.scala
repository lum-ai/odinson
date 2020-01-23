package ai.lum.odinson.digraph

case class TraversedStep(
  from: Int,
  to: Int,
  edgeLabel: Int,
  direction: String,
)

class TraversedPath(val start: Int) {

  def end: Int = reverseSteps.headOption.map(_.to).getOrElse(start)

  def path: Seq[TraversedStep] = reverseSteps.reverse

  private var reverseSteps: List[TraversedStep] = Nil

  def addStep(step: TraversedStep): Unit = {
    reverseSteps = step :: reverseSteps
  }

  def copy(): TraversedPath = {
    val that = new TraversedPath(this.start)
    // reverseSteps is an immutable list
    that.reverseSteps = this.reverseSteps
    that
  }

}
