package ai.lum.odinson.foundations

import ai.lum.odinson.DataGatherer.VerboseLevels
import ai.lum.odinson.utils.TestUtils.OdinsonTest
import ai.lum.odinson.utils.exceptions.OdinsonException

class TestMention extends OdinsonTest {

  val rules =
    """
      |rules:
      |  - name: bears-rule
      |    label: Bear
      |    type: event
      |    priority: 1
      |    pattern: |
      |      trigger = bears
      |      bearType = >amod []
      |""".stripMargin

  behavior of "Mention"

  it should "not be populated unless asked to be" in {
    val ee = mkExtractorEngine("becky-gummy-bears-v2")
    val mentionsBase = ee.extractMentions(ee.compileRuleString(rules)).toArray
    mentionsBase should have size (2) // the main mention and the untyped arg
    an[OdinsonException] shouldBe thrownBy(mentionsBase.filter(_.label.isDefined).head.text)

    ee.clearState()

    val mentionsPopulated = ee.extractAndPopulate(ee.compileRuleString(rules)).toArray
    mentionsPopulated should have size (2)
    mentionsPopulated.filter(_.label.isDefined).head.text should be("bears")
  }

  it should "be populated to a certain level when asked" in {
    val doc = getDocument("becky-gummy-bears-v2")
    val ee = extractorEngineWithSentenceStoredFields(doc, Seq("raw", "lemma"))
    val mentions = ee.extractMentions(ee.compileRuleString(rules)).toArray
    mentions should have size (2) // the main mention and the untyped arg
    val event = mentions.filter(_.label.isDefined).head
    event.documentFields.keySet should have size (0)
    event.mentionFields.keySet should have size (0)

    event.populateFields(VerboseLevels.Display)
    event.hasFieldsPopulated(VerboseLevels.Display) shouldBe true
    event.documentFields.keySet should contain only "raw"
    event.mentionFields.keySet should contain only "raw"

    event.populateFields(VerboseLevels.All)
    event.hasFieldsPopulated(VerboseLevels.All) shouldBe true
    event.hasFieldsPopulated(VerboseLevels.Display) shouldBe true
    event.documentFields.keySet should contain theSameElementsAs Seq("raw", "lemma")
    event.mentionFields.keySet should contain theSameElementsAs Seq("raw", "lemma")

  }

  it should "populate arguments when populated" in {
    val doc = getDocument("becky-gummy-bears-v2")
    val ee = extractorEngineWithSentenceStoredFields(doc, Seq("raw", "lemma"))
    val mentions = ee.extractMentions(ee.compileRuleString(rules)).toArray
    mentions should have size (2) // the main mention and the untyped arg
    val event = mentions.filter(_.label.isDefined).head
    event.documentFields.keySet should have size (0)
    event.mentionFields.keySet should have size (0)

    event.populateFields(VerboseLevels.Display)
    event.hasFieldsPopulated(VerboseLevels.Display) shouldBe true
    val bearType = event.arguments("bearType").head
    bearType.hasFieldsPopulated(VerboseLevels.Display) shouldBe true
    bearType.documentFields.keySet should contain only ("raw")
    bearType.mentionFields.keySet should contain only ("raw")
  }

  it should "produce mention copies that are populated at the same level" in {
    val doc = getDocument("becky-gummy-bears-v2")
    val ee = extractorEngineWithSentenceStoredFields(doc, Seq("raw", "lemma"))
    val mentions = ee.extractMentions(ee.compileRuleString(rules)).toArray
    mentions should have size (2) // the main mention and the untyped arg
    val event = mentions.filter(_.label.isDefined).head
    event.documentFields.keySet should have size (0)
    event.mentionFields.keySet should have size (0)

    event.populateFields(VerboseLevels.Display)
    event.hasFieldsPopulated(VerboseLevels.Display) shouldBe true

    val copy = event.copy(label = Some("NewEvent"))
    copy == event shouldBe (false)
    copy.hasFieldsPopulated(VerboseLevels.Display) shouldBe (true)
    copy.hasFieldsPopulated(VerboseLevels.All) shouldBe (false)
  }

}
