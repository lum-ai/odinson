package ai.lum.odinson.state

import ai.lum.odinson.OdinsonMatch

// This should include everything to make a mention, whether or not it is used yet.
case class ResultItem(
  segmentDocBase: Int, segmentDocId: Int, docIndex: Int,
  label: String, name: String,
  odinsonMatch: OdinsonMatch
) {

  def size: Int = ResultItem.sizeOf(odinsonMatch)

// Optionally store the docId and sentId now so that they don't have to be looked up later.
//  val document: Document = extractorEngine.doc(docIndex)
//  val docId = document.getField("docId").stringValue
//  val sentId= document.getField("sentId").stringValue
}

object ResultItem {

  def sizeOf(odinsonMatch: OdinsonMatch): Int = {
    odinsonMatch.namedCaptures.foldLeft(1) { case (total, namedCapture) =>
      total + sizeOf(namedCapture.capturedMatch)
    }
  }
}