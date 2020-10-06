package ai.lum.odinson.mention

class NullIdGetter() extends LazyIdGetter(null, 0) {
  override lazy val document = ???
  override lazy val docId: String = "NULL"
  override lazy val sentId: String = "NULL"

  override def getDocId: String = "NULL"

  override def getSentId: String = "NULL"
}

object NullIdGetter {
  // The x: Int is to fit the pattern of the mruIdGetter
  def apply(x: Int): NullIdGetter = new NullIdGetter()
}