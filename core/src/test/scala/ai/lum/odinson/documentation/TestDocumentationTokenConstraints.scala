package ai.lum.odison.documentation

import org.scalatest._

import ai.lum.odinson.ExtractorEngine
import ai.lum.odinson.BaseSpec
import ai.lum.odinson.Document

class TestDocumentationTokenConstraints extends BaseSpec {
  def exampleSentence: String = 
     """{"id":"dd","metadata":[],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["George","ate","gummy","bears","."],"store":true},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["George","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["george","eat","gummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["ORGANIZATION","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}"""

  "Documentation-TokenConstraints" should "work for 'Example'" in {
    val ee = this.Utils.mkExtractorEngine("The dog barks")
    // what is there should match
    val q = ee.compiler.mkQuery("dog")
    val s = ee.query(q)
    s.totalHits shouldEqual (1)
    // something that is not there should not match
    val q1 = ee.compiler.mkQuery("cat")
    val s1 = ee.query(q1)
    s1.totalHits shouldEqual (0)
  }
  
  "Documentation-TokenConstraints" should "work for 'Using the token fields'" in {
    val doc = Document.fromJson(exampleSentence)
    val ee = this.Utils.mkExtractorEngine(doc)
    // [tag=/N.*/]
    // get a document with tags
    val q = ee.compiler.mkQuery("[tag=/N*./]")
    val s = ee.query(q)
    s.totalHits shouldEqual (1)
    // 
    val q1 = ee.compiler.mkQuery("[tag=/V*./]")
    val s1 = ee.query(q1)
    s1.totalHits shouldEqual (1)
  }
  
  "Documentation-TokenConstraints" should "work for 'Operators for token constraints'" in {
    val doc = Document.fromJson(exampleSentence)
    val ee = this.Utils.mkExtractorEngine(doc)
    // [tag=/N.*/ & (entity=ORGANIZATION | tag=NNP)]
    val q = ee.compiler.mkQuery("[tag=/N.*/ & (entity=ORGANIZATION | tag=NNP)]")
    val s = ee.query(q)
    s.totalHits shouldEqual (1)
    // should not return
    val q1 = ee.compiler.mkQuery("[tag=/N.*/ & (entity=FOO | tag=BAR)]")
    val s1 = ee.query(q1)
    s1.totalHits shouldEqual (0)
  }
  
  "Documentation-TokenConstraints" should "work for 'Wildcards'" in {
    val doc = Document.fromJson(exampleSentence)
    val ee = this.Utils.mkExtractorEngine(doc)

    // testing wilcard
    val q = ee.compiler.mkQuery("[]")
    // make sure it compiles to the right thing
    q.toString shouldEqual ("AllNGramsQuery(1)")
    val s = ee.query(q)
    s.totalHits shouldEqual (1)
  }
}
