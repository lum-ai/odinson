package ai.lum.odinson.state.sql

class IdProvider(protected var id: Int = 0) {

  def next: Int = {
    val result = id

    id += 1
    result
  }
}
