package ai.lum.odison.documentation

import ai.lum.odinson.utils.TestUtils.OdinsonTest

class TestDocumentationGraphTraversals extends OdinsonTest {
  val doc = getDocument("becky-gummy-bears")
  "Odinson TestDocumentationGraphTraversals" should "work for '>foo' example" in {
    val ee = mkExtractorEngine(doc)
    // what is there should match
    val pattern = """
      trigger = [lemma=eat]
      object: ^NP = >dobj
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val s = ee.query(q)
    // something that is not there should not match
    s.totalHits shouldEqual (1)
    testEventTrigger(s.scoreDocs.head.matches.head, start = 1, end = 2)
    val desiredArgs = Seq(
      ArgumentOffsets("object", 3, 4)
    )
    testArguments(s.scoreDocs.head.matches.head, desiredArgs)
  }
 
  it should "work for '<foo' example" in {
    val ee = mkExtractorEngine(doc)
    // what is there should match
    val pattern = """
      trigger = [lemma=gummy]
      object: ^NP = </amod|xcomp/
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val s = ee.query(q)
    // something that is not there should not match
    s.totalHits shouldEqual (1)
    testEventTrigger(s.scoreDocs.head.matches.head, start = 2, end = 3)
    val desiredArgs = Seq(
      ArgumentOffsets("object", 3, 4)
    )
    testArguments(s.scoreDocs.head.matches.head, desiredArgs)
  }
  
  it should "work for '<<' example" in {
    val ee = mkExtractorEngine(doc)
    // what is there should match
    val pattern = """
      trigger = [lemma=gummy]
      object: ^NP = <<
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val s = ee.query(q)
    // something that is not there should not match
    s.totalHits shouldEqual (1)
    testEventTrigger(s.scoreDocs.head.matches.head, start = 2, end = 3)
    val desiredArgs = Seq(
      ArgumentOffsets("object", 3, 4)
    )
    testArguments(s.scoreDocs.head.matches.head, desiredArgs)
  }

  // make sure it matches the correct thing
  it should "work for '>>' example" in {
    val ee = mkExtractorEngine(doc)
    // what is there should match
    val pattern = """
      trigger = [lemma=bear]
      object: ^NP = >>
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val s = ee.query(q)
    // something that is not there should not match
    s.totalHits shouldEqual (1)
    testEventTrigger(s.scoreDocs.head.matches.head, start = 3, end = 4)
    val desiredArgs = Seq(
      ArgumentOffsets("object", 2, 3)
    )
    testArguments(s.scoreDocs.head.matches.head, desiredArgs)
  }
  
  it should "work for '>>{2,3}' example" in {
    val ee = mkExtractorEngine(doc)
    // what is there should match
    val pattern = """
      trigger = [lemma=eat]
      object: ^NP = >>{2,3}
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val s = ee.query(q)
    // something that is not there should not match
    s.totalHits shouldEqual (1)
    testEventTrigger(s.scoreDocs.head.matches.head, start = 1, end = 2)
    val desiredArgs = Seq(
      ArgumentOffsets("object", 2, 3)
    )
    testArguments(s.scoreDocs.head.matches.head, desiredArgs)
    // this should not match
    val pattern1 = """
      trigger = [lemma=bear]
      object: ^NP = >>{2,3}
    """
    val q1 = ee.compiler.compileEventQuery(pattern1)
    val s1 = ee.query(q1)
    s1.totalHits shouldEqual (0)
  }
} 
