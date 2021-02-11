package ai.lum.odinson.traversals

import ai.lum.odinson.utils.TestUtils.OdinsonTest

class TestTraversals extends OdinsonTest {

  val eeAlien = mkExtractorEngine("alien-species")

  // Some wild animals such as hedgehogs , coypu , and any wild cloven-footed animals
  // such as deer and zoo animals including elephants can also contract it .
  val eeHedgehogs = mkExtractorEngine("hedgehogs-coypy-2")

  // John ate ramen with chopsticks and a spoon.
  val eeSpoon = mkExtractorEngine("chopsticks-spoon")

  "Odinson" should "find all matches across conj_and" in {
    val pattern = "[word=cats] >conj_and [tag=/N.*/]"
    val query = eeAlien.compiler.mkQuery(pattern)
    val results = eeAlien.query(query, 1)
    results.totalHits should equal(1)
    results.scoreDocs.head.matches should have size 2
    val doc = results.scoreDocs.head.doc
    val Array(m1, m2) = results.scoreDocs.head.matches
    eeAlien.getStringForSpan(doc, m1) should equal("horses")
    eeAlien.getStringForSpan(doc, m2) should equal("cattle")
  }

  it should "support parentheses surrounding graph traversals AND surface patterns" in {
    val pattern = "[word=cats] (>conj_and [tag=/N.*/])"
    val query = eeAlien.compiler.mkQuery(pattern)
    val results = eeAlien.query(query, 1)
    results.totalHits should equal(1)
    results.scoreDocs.head.matches should have size 2
    val doc = results.scoreDocs.head.doc
    val Array(m1, m2) = results.scoreDocs.head.matches
    eeAlien.getStringForSpan(doc, m1) should equal("horses")
    eeAlien.getStringForSpan(doc, m2) should equal("cattle")
  }

  // A little helper method to reduce code duplication for the following tests
  def testHedgehogQuantifier(
    quantifier: String,
    expectedMatches: Array[String]
  ) = {
    val pattern = s"[word=animals] (>nmod_such_as [])${quantifier}"
    val query = eeHedgehogs.compiler.mkQuery(pattern)
    val results = eeHedgehogs.query(query, 1)
    results.totalHits should equal(1)
    val matches = results.scoreDocs.head.matches
    matches should have size expectedMatches.length
    val doc = results.scoreDocs.head.doc
    val foundStrings = matches.map(m => eeHedgehogs.getStringForSpan(doc, m))
    foundStrings shouldEqual expectedMatches
  }

  it should "support quantifiers on groups of graph traversals and surface patterns -- optional" in {
    testHedgehogQuantifier(
      "?",
      Array("animals", "hedgehogs", "coypu", "yyymals")
    )
  }

  it should "support quantifiers on groups of graph traversals and surface patterns -- ranges {1}" in {
    testHedgehogQuantifier("{1}", Array("hedgehogs", "coypu", "yyymals"))
  }

  it should "support quantifiers on groups of graph traversals and surface patterns -- ranges {2}" in {
    testHedgehogQuantifier("{2}", Array("deer", "zzzmals"))
  }

  it should "support quantifiers on groups of graph traversals and surface patterns -- ranges {1,2}" in {
    testHedgehogQuantifier(
      "{1,2}",
      Array("hedgehogs", "coypu", "yyymals", "deer", "zzzmals")
    )
  }

  it should "support quantifiers on groups of graph traversals and surface patterns -- kleene plus" in {
    testHedgehogQuantifier(
      "+",
      Array("hedgehogs", "coypu", "yyymals", "deer", "zzzmals")
    )
  }

  it should "support quantifiers on groups of graph traversals and surface patterns -- kleene star" in {
    testHedgehogQuantifier(
      "*",
      Array("animals", "hedgehogs", "coypu", "yyymals", "deer", "zzzmals")
    )
  }

  def testSpoonExpanding(pattern: String, expectedMatches: Array[String]) = {
    val query = eeSpoon.compiler.mkQuery(pattern)
    val results = eeSpoon.query(query, 1)
    results.totalHits should equal(1)
    val matches = results.scoreDocs.head.matches
    val doc = results.scoreDocs.head.doc
    val foundStrings = matches.map(m => eeSpoon.getStringForSpan(doc, m))
    foundStrings shouldEqual expectedMatches
  }

  it should "support expanding the match to a larger context -- base query, no expansion" in {
    val pattern = "John <nsubj >nmod_with []"
    val expected = Array("chopsticks")
    testSpoonExpanding(pattern, expected)
  }

  it should "support expanding the match to a larger context -- base, with trivial case no expansion pattern" in {
    val pattern = "John <nsubj >nmod_with (?^ [])"
    val expected = Array("chopsticks")
    testSpoonExpanding(pattern, expected)
  }

  it should "support expanding the match to a larger context -- with expansion" in {
    val pattern = "John <nsubj >nmod_with (?^ [] >conj [])"
    val expected = Array("chopsticks and a spoon")
    testSpoonExpanding(pattern, expected)
  }

  it should "support expanding the match to a larger context -- with optional expansion" in {
    val pattern = "John <nsubj >nmod_with (?^ [] (>conj [])?)"
    val expected = Array("chopsticks", "chopsticks and a spoon")
    testSpoonExpanding(pattern, expected)
  }

}
