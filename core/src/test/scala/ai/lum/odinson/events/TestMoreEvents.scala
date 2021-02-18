package ai.lum.odinson.events

import ai.lum.odinson.utils.TestUtils.OdinsonTest

class TestMoreEvents extends OdinsonTest {

  def ee = mkExtractorEngine("chopsticks-spoon")

  "Odinson" should "find two events with one tool each" in {
    val pattern = """
      trigger = [lemma=eat]
      theme: ^food = >dobj
      tool: ^tool = >nmod_with >conj?
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 5)
    results.totalHits should equal(1)
    results.scoreDocs.head.matches should have size 2
    val Array(m1, m2) = results.scoreDocs.head.matches
    // test trigger
    testEventTrigger(m1, start = 1, end = 2)
    testEventTrigger(m2, start = 1, end = 2)
    // test arguments
    val desiredArgs1 = Seq(ArgumentOffsets("theme", 2, 3), ArgumentOffsets("tool", 4, 5))
    val desiredArgs2 = Seq(ArgumentOffsets("theme", 2, 3), ArgumentOffsets("tool", 7, 8))
    testArguments(m1, desiredArgs1)
    testArguments(m2, desiredArgs2)
    ee.clearState()
  }

  it should "find one events with two tools" in {
    val pattern = """
      trigger = [lemma=eat]
      theme: ^food = >dobj
      tool: ^tool+ = >nmod_with >conj?
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 5)
    results.totalHits should equal(1)
    results.scoreDocs.head.matches should have size 1
    val Array(m1) = results.scoreDocs.head.matches
    // test trigger
    testEventTrigger(m1, start = 1, end = 2)
    // test arguments
    val desiredArgs1 = Seq(
      ArgumentOffsets("theme", 2, 3),
      ArgumentOffsets("tool", 4, 5),
      ArgumentOffsets("tool", 7, 8)
    )
    testArguments(m1, desiredArgs1)
    ee.clearState()
  }

  it should "find two events, one with two tools, and one with zero" in {
    val pattern = """
      trigger = [lemma=eat]
      theme: ^food = >dobj
      tool: ^tool* = >nmod_with >conj?
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 5)
    results.totalHits should equal(2)
    results.scoreDocs(0).matches should have size 1
    val Array(m1) = results.scoreDocs(0).matches
    // test trigger
    testEventTrigger(m1, start = 1, end = 2)
    // test arguments
    val desiredArgs1 = Seq(
      ArgumentOffsets("theme", 2, 3),
      ArgumentOffsets("tool", 4, 5),
      ArgumentOffsets("tool", 7, 8)
    )
    testArguments(m1, desiredArgs1)
    results.scoreDocs(1).matches should have size 1
    val Array(m2) = results.scoreDocs(1).matches
    // test trigger
    testEventTrigger(m2, start = 1, end = 2)
    // test arguments
    val desiredArgs2 = Seq(ArgumentOffsets("theme", 2, 3))
    testArguments(m2, desiredArgs2)
    ee.clearState()
  }

  it should "find two events with one tool each even if theme is optional" in {
    val pattern = """
      trigger = [lemma=eat]
      theme: ^food? = >dobj
      tool: ^tool = >nmod_with >conj?
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 5)
    results.totalHits should equal(1)
    results.scoreDocs.head.matches should have size 2
    val Array(m1, m2) = results.scoreDocs.head.matches
    // test trigger
    testEventTrigger(m1, start = 1, end = 2)
    testEventTrigger(m2, start = 1, end = 2)
    // test arguments
    val desiredArgs1 = Seq(ArgumentOffsets("theme", 2, 3), ArgumentOffsets("tool", 4, 5))
    val desiredArgs2 = Seq(ArgumentOffsets("theme", 2, 3), ArgumentOffsets("tool", 7, 8))
    testArguments(m1, desiredArgs1)
    testArguments(m2, desiredArgs2)
    ee.clearState()
  }

  it should "not find events with both tool and location" in {
    val pattern = """
      trigger = [lemma=eat]
      theme: ^food = >dobj
      tool: ^tool = >nmod_with >conj?
      location: ^place = >nmod_at
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 5)
    results.totalHits should equal(0)
    ee.clearState()
  }

  it should "find three events when theme, tool, and location are optional" in {
    val pattern = """
      trigger = [lemma=eat]
      theme: ^food? = >dobj
      tool: ^tool? = >nmod_with >conj?
      location: ^place? = >nmod_at
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 5)
    results.totalHits should equal(2)
    // sentence 1
    results.scoreDocs(0).matches should have size 2
    val Array(m1, m2) = results.scoreDocs(0).matches
    // test trigger
    testEventTrigger(m1, start = 1, end = 2)
    testEventTrigger(m2, start = 1, end = 2)
    // test arguments
    val desiredArgs1 = Seq(ArgumentOffsets("theme", 2, 3), ArgumentOffsets("tool", 4, 5))
    val desiredArgs2 = Seq(ArgumentOffsets("theme", 2, 3), ArgumentOffsets("tool", 7, 8))
    testArguments(m1, desiredArgs1)
    testArguments(m2, desiredArgs2)
    // sentence 2
    results.scoreDocs(1).matches should have size 1
    val Array(m3) = results.scoreDocs(1).matches
    // test trigger
    testEventTrigger(m3, start = 1, end = 2)
    // test arguments
    val desiredArgs3 = Seq(ArgumentOffsets("theme", 2, 3), ArgumentOffsets("location", 5, 6))
    testArguments(m3, desiredArgs3)
    ee.clearState()
  }

  it should "find one event with required location" in {
    val pattern = """
      trigger = [lemma=eat]
      theme: ^food = >dobj
      tool: ^tool? = >nmod_with >conj?
      location: ^place = >nmod_at
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 5)
    results.totalHits should equal(1)
    results.scoreDocs(0).matches should have size 1
    val Array(m1) = results.scoreDocs(0).matches
    // test trigger
    testEventTrigger(m1, start = 1, end = 2)
    // test arguments
    val desiredArgs1 = Seq(ArgumentOffsets("theme", 2, 3), ArgumentOffsets("location", 5, 6))
    testArguments(m1, desiredArgs1)
    ee.clearState()
  }
}
