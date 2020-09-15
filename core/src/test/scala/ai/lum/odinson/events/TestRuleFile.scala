package ai.lum.odinson.events

import ai.lum.common.FileUtils._
import java.io.File
import java.nio.file.{Files, Path}

import ai.lum.odinson.utils.exceptions.OdinsonException

class TestRuleFile extends EventSpec {
  // extractor engine persists across tests (hacky way)
  def docGummy = getDocument("becky-gummy-bears-v2")
  def eeGummy = Utils.mkExtractorEngine(docGummy)

  // Leonardo leads, Donatello does machines (That's a fact, jack!)
  val docNinja = getDocument("ninja-turtles")
  val eeNinja = Utils.mkExtractorEngine(docNinja)

  "Odinson" should "match event with rules defined in a rule file" in {
    val rules = """
      |vars:
      |  chunk: "[chunk=B-NP][chunk=I-NP]*"
      |
      |rules:
      |  - name: testrule
      |    type: event
      |    pattern: |
      |      trigger = [lemma=eat]
      |      subject: ^NP = >nsubj ${chunk}
      |      object: ^NP = >dobj ${chunk}
    """.stripMargin
    val extractors = eeGummy.ruleReader.compileRuleString(rules)
    val mentions = eeGummy.extractMentions(extractors)
    mentions should have size 1
    val m = mentions.head.odinsonMatch
    // test trigger
    testEventTrigger(m, start = 1, end = 2)
    // test arguments
    val desiredArgs = Seq(
      createArgument("subject", 0, 1),
      createArgument("object", 2, 4),
    )
    testEventArguments(m, desiredArgs)
  }

  it should "correctly handle list vars" in {
    val rules =
      """
        |vars:
        |  turtle:
        |     - leonardo
        |     - donatello
        |     - raphael
        |     - michelangelo
        |
        |rules:
        |  - name: "turtle-power-var"
        |    label: MutantTurtle
        |    type: basic
        |    pattern: |
        |      [norm=/${turtle}/]
       """.stripMargin
    val extractors = eeNinja.compileRuleString(rules)
    val mentions = eeNinja.extractMentions(extractors)
    mentions should have size 2
  }

  it should "allow rules to be imported in a master rule file" in {
    val masterPath = "/testGrammar/testMaster.yml"
    val extractors = eeGummy.compileRuleResource(masterPath)
    val mentions = eeGummy.extractMentions(extractors)
    mentions should have size 1
    // Tests that the variables from the master file propagate
    mentions.head.foundBy should be("testRuleImported-IMPORT_LABEL")
  }

  // todo:
  //  1: import in a string should throw an exception
  it should "throw an exception with imports in string" in {
    // Leonardo leads, Donatello does machines (That's a fact, jack!)
    val doc = getDocument("ninja-turtles")
    val ee = Utils.mkExtractorEngine(doc)
    val rules =
      """
        |vars:
        |  turtle:
        |     - leonardo
        |     - donatello
        |     - raphael
        |     - michelangelo
        |
        |rules:
        |  - import: /testGrammar/testRules.yml
        |
       """.stripMargin
    assertThrows[OdinsonException]{
      ee.compileRuleString(rules)
    }
  }
  //  2: a string w/o import should not throw an exception -- see above

  //  3: test resources with absolute and relative paths (with .. notation)
  //  5: test that the hard-coded > import > parent > local
  it should "allow resource file imports with absolute and relative paths and handle variables" in {
    val masterPath = "/testGrammar/testPaths.yml"
    val extractors = eeNinja.compileRuleResource(masterPath, Map("otherName" -> "HARD_CODED"))
    val mentions = eeNinja.extractMentions(extractors)
    mentions should have size 3
    val leadsMentions = mentions.filter(m => eeNinja.getStringForSpan(m.luceneDocId, m.odinsonMatch) == "leads")
    assert(leadsMentions.length == 1)
    // because import beats both parent (in a.yml) and local (in b.yml)
    leadsMentions.head.foundBy should be("B-IMPORT_FROM_A")

    val machinesMentions = mentions.filter(m => eeNinja.getStringForSpan(m.luceneDocId, m.odinsonMatch) == "machines")
    assert(machinesMentions.length == 1)
    // because import beats both parent and local
    machinesMentions.head.foundBy should be("A-IMPORT_NAME")

    val factMentions = mentions.filter(m => eeNinja.getStringForSpan(m.luceneDocId, m.odinsonMatch) == "fact")
    assert(factMentions.length == 1)
    // no import, but parent beats local, and hard-coded trumps all
    factMentions.head.foundBy should be("C-C-PARENT-HARD_CODED")
  }

  //  4: test filesystem resources -- write temp directory with files, put a grammar there, delete when done (if possible)
  it should "allow for absolute and relative paths in filesystem" in {
    val tempDir = Files.createTempDirectory("tmp-grammar").toFile
    tempDir.deleteOnExit()

    val masterFile = new File(tempDir, "tmpMaster.yml")
    masterFile.deleteOnExit()

    val importDir = Files.createDirectory(new File(tempDir, "imported").toPath).toFile
    importDir.deleteOnExit()

    val aFile = new File(importDir, "a.yml")
    aFile.deleteOnExit()

    val bFile = new File(importDir, "b.yml")
    bFile.deleteOnExit()

    val bRelative = new File("imported", "b.yml")

    val masterContents =
      s"""
        |vars:
        |  chunk: "[chunk=B-NP][chunk=I-NP]*"
        |
        |rules:
        |  - import: ${aFile.getAbsolutePath}
        |
        |  - import: ${bRelative}
        """.stripMargin

    val aContents =
      """
        |rules:
        |  - name: A
        |    type: basic
        |    pattern: |
        |      machines
        """.stripMargin

    val bContents =
      """
        |rules:
        |  - name: B
        |    type: basic
        |    pattern: |
        |      leads
        """.stripMargin

    masterFile.writeString(masterContents)
    aFile.writeString(aContents)
    bFile.writeString(bContents)

    val extractors = eeNinja.compileRuleFile(masterFile)
    val mentions = eeNinja.extractMentions(extractors)
    mentions should have size 2
    val leadsMentions = mentions.filter(m => eeNinja.getStringForSpan(m.luceneDocId, m.odinsonMatch) == "leads")
    assert(leadsMentions.length == 1)

    val machinesMentions = mentions.filter(m => eeNinja.getStringForSpan(m.luceneDocId, m.odinsonMatch) == "machines")
    assert(machinesMentions.length == 1)
  }

  it should "allow for importing vars from a resource" in {
    val masterPath = "/testGrammar/varImports/rules.yml"
    val extractors = eeNinja.compileRuleResource(masterPath)
    val mentions = eeNinja.extractMentions(extractors)
    mentions should have size 1
    val leadsMentions = mentions.filter(m => eeNinja.getStringForSpan(m.luceneDocId, m.odinsonMatch) == "leads")
    assert(leadsMentions.length == 1)
    leadsMentions.head.foundBy should be("leads-IMPORTED_FROM_VARS")
  }

  it should "allow for importing vars from filesystem" in {
    val tempDir = Files.createTempDirectory("tmp-grammar").toFile
    tempDir.deleteOnExit()

    val rulesFile = new File(tempDir, "rules.yml")
    rulesFile.deleteOnExit()

    val varsFile = new File(tempDir, "vars.yml")
    varsFile.deleteOnExit()

    val rulesContents =
      """
         |vars: vars.yml
         |
         |rules:
         |  - name: B-${name}
         |    type: basic
         |    pattern: |
         |      leads
        """.stripMargin

    val varsContents =
      """
        |name: IMPORTED_NAME
        """.stripMargin

    rulesFile.writeString(rulesContents)
    varsFile.writeString(varsContents)

    val extractors = eeNinja.compileRuleFile(rulesFile)
    val mentions = eeNinja.extractMentions(extractors)
    mentions should have size 1
    val leadsMentions = mentions.filter(m => eeNinja.getStringForSpan(m.luceneDocId, m.odinsonMatch) == "leads")
    assert(leadsMentions.length == 1)
    leadsMentions.head.foundBy should be("B-IMPORTED_NAME")


  }
}
