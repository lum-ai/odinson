package ai.lum.odinson.foundations

import ai.lum.odinson.{Document, ExtractorEngine}
import ai.lum.odinson.utils.TestUtils.OdinsonTest

class TestCycleHandling extends OdinsonTest {

  behavior of "Odinson"

  it should "not infinitely loop when there are cycles in the graph and nested kleene quantifiers" in {
    // this syntax graph has been altered to have (a) all deps have label XXX and (b) 0 -> 1 -> 2 -> 3 -> 0
    val docJson = """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","gummy","bears","."],"store":true},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","gummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[0,1,"XXX"],[1,2,"XXX"],[2,3,"XXX"],[3,0,"XXX"]],"roots":[1]}]}]}"""
    val doc = Document.fromJson(docJson)
    val ee = ExtractorEngine.inMemory(doc)
    var pattern = "[] >XXX+ []"
    ee.query(ee.mkQuery(pattern)).scoreDocs.length shouldBe (1)
    pattern = "[] (>XXX+ [])*"
    ee.query(ee.mkQuery(pattern)).scoreDocs.length shouldBe (1)
    pattern = "[] (>XXX* [])+"
    ee.query(ee.mkQuery(pattern)).scoreDocs.length shouldBe (1)
    pattern = "[] (>XXX+ [])+"
    ee.query(ee.mkQuery(pattern)).scoreDocs.length shouldBe (1)
    pattern = "[] (>XXX* [])*"
    ee.query(ee.mkQuery(pattern)).scoreDocs.length shouldBe (1)
  }


}
