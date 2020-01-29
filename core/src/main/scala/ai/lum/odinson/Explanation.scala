package ai.lum.odinson

// FIXME
// this is currently an explanation for a graph traversal
// but the plan is to turn this into an explanation for any match
// so anything about this class is subject to change
// use at your own risk
class Explanation(
  val sentence: String,
  val src: String,
  val dst: String,
  val path: String,
  val lexicalizedPath: String,
)
