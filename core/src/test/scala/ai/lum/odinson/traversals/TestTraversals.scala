package ai.lum.odinson.traversals

import ai.lum.odinson.BaseSpec

class TestTraversals extends BaseSpec {

  val docAlien = getDocument("alien-species")
  val eeAlien = Utils.mkExtractorEngine(docAlien)

  //val doc = Document.fromJson(json)
  val doc = getDocumentFromJson(json)
  val ee = Utils.mkExtractorEngine(doc)

  "Odinson" should "find all matches across conj_and" in {
    val pattern = "[word=cats] >conj_and [tag=/N.*/]"
    val query = eeAlien.compiler.mkQuery(pattern)
    val results = eeAlien.query(query, 1)
    results.totalHits should equal (1)
    results.scoreDocs.head.matches should have size 2
    val doc = results.scoreDocs.head.doc
    val Array(m1, m2) = results.scoreDocs.head.matches
    eeAlien.getString(doc, m1) should equal ("horses")
    eeAlien.getString(doc, m2) should equal ("cattle")
  }

  it should "support parentheses surrounding graph traversals AND surface patterns" in {
    val pattern = "[word=cats] (>conj_and [tag=/N.*/])"
    val query = eeAlien.compiler.mkQuery(pattern)
    val results = eeAlien.query(query, 1)
    results.totalHits should equal (1)
    results.scoreDocs.head.matches should have size 2
    val doc = results.scoreDocs.head.doc
    val Array(m1, m2) = results.scoreDocs.head.matches
    eeAlien.getString(doc, m1) should equal ("horses")
    eeAlien.getString(doc, m2) should equal ("cattle")
  }

}
