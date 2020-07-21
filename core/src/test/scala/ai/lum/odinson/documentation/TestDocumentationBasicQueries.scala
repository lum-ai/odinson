package ai.lum.odison.documentation

import org.scalatest._

import ai.lum.odinson.ExtractorEngine
import ai.lum.odinson.BaseSpec
//import ai.lum.odinson.events.EventSpec
import ai.lum.odinson.{Document, OdinsonMatch}

class TestDocumentationBasicQueries extends BaseSpec {
  def doc: Document =
    Document.fromJson(
      """{"id":"george-what?-bears","metadata":[],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["George","and","dog","bears","."],"store":true},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["George","and","dog","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["george","and","dog","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["ORGANIZATION","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","I-NP","I-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}"""
    )
  // [tag=/N.*/] and [lemma=dog]
  "Documentation-BasicQueries" should "work 'for surface patterns'" in {
    val ee = this.Utils.mkExtractorEngine(doc)
    // what is there should match
    val q = ee.compiler.mkQuery("[tag=/N.*/] and [lemma=dog]")
    val s = ee.query(q)
    s.totalHits shouldEqual (1)
    s.scoreDocs.head.matches.head.start shouldEqual (0)
    s.scoreDocs.head.matches.head.end shouldEqual (3)
  }

  // (?<animal> [tag=/N.*/]) and [lemma=dog]
  "Documentation-BasicQueries" should "work 'named captures'" in {
    val ee = this.Utils.mkExtractorEngine(doc)
    // what is there should match
    val q = ee.compiler.mkQuery("(?<animal> [tag=/N.*/]) and [lemma=dog]")
    val s = ee.query(q)
    val matchval: OdinsonMatch = s.scoreDocs.head.matches.head
    matchval.namedCaptures.length shouldEqual 1
    matchval.namedCaptures.head.name shouldEqual ("animal")
    val nameCapturedVal = matchval.namedCaptures.head.capturedMatch
    nameCapturedVal.start shouldEqual (0)
    nameCapturedVal.end shouldEqual (1)
  }

  // (?<controller> [entity=PROTEIN]) <nsubj phosphorilates >dobj (?<theme> [entity=PROTEIN])
  //"Documentation-BasicQueries" should "work 'adding syntax through graph traversals'" in {}
  // TODO: combining representations
}
