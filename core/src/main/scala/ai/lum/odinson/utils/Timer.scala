package ai.lum.odinson.utils.Timer

import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable

class Timer(val description: String) {
  var elapsedTime: Option[Long] = None
  var startTime: Option[Long] = None

  def time[R](block: => R): R = {
    val t0 = System.currentTimeMillis()
    val result: R = block    // call-by-name
    val t1 = System.currentTimeMillis()

    elapsedTime = Some(t1 - t0)
    result
  }

  def start(): Unit = {
    val t0 = System.currentTimeMillis()

    startTime = Some(t0)
  }

  def stop(): Unit = {
    if (startTime.isDefined) {
      val t1 = System.currentTimeMillis()

      elapsedTime = Some(t1 - startTime.get)
    }
  }

  override def toString: String = {
    if (elapsedTime.isDefined)
      s"\tTime\t$description\t${Timer.diffToString(elapsedTime.get)}"
    else if (startTime.isDefined)
      s"\tStart\t$description\t${startTime.get}\tms"
    else
      s"\tTimer\t$description"
  }
}

object Timer {
  protected lazy val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val elapsedTimes: mutable.Map[String, Long] = mutable.Map.empty

  def addTime(key: String, milliseconds: Long): Unit = this synchronized {
    val oldTime = elapsedTimes.getOrElseUpdate(key, 0)
    val newTime = oldTime + milliseconds

    elapsedTimes.update(key, newTime)
  }

  def summarize: Unit = {
    elapsedTimes.toSeq.sorted.foreach { case (key, milliseconds) =>
      logger.info(s"\tTotal\t$key\t$milliseconds")
    }
  }

  def diffToString(diff: Long): String = {
    val  days = (diff / (1000 * 60 * 60 * 24)) / 1
    val hours = (diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)
    val  mins = (diff % (1000 * 60 * 60)) / (1000 * 60)
    val  secs = (diff % (1000 * 60)) / 1000
    val msecs = (diff % (1000 * 1)) / 1

    f"$days:$hours%02d:$mins%02d:$secs%02d.$msecs%03d"
  }

  // See http://biercoff.com/easily-measuring-code-execution-time-in-scala/
  def time[R](description: String, verbose: Boolean = true)(block: => R): R = {
    val t0 = System.currentTimeMillis()
    if (verbose) println(s"\tStart\t$description\t$t0\tms")

    val result: R = block // call-by-name

    val t1 = System.currentTimeMillis()
    if (verbose) println(s"\tStop\t$description\t$t1\tms")

    val diff = t1 - t0
    if (verbose) println(s"\tDiff\t$description\t$diff\tms")
    if (verbose) println(s"\tTime\t$description\t${diffToString(diff)}")
    addTime(description, diff)
    result
  }
}
