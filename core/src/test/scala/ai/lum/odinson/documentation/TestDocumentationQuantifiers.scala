package ai.lum.odison.documentation

import org.scalatest._

import ai.lum.odinson.ExtractorEngine
import ai.lum.odinson.BaseSpec
import ai.lum.odinson.events.EventSpec
import ai.lum.odinson.{Document, OdinsonMatch}

class TestDocumentationQuantifiers extends EventSpec {
  def doc: Document =
    Document.fromJson(
      """{"id":"phoshorilation","metadata":[],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Foo","phosphorilates","bar","bears","."],"store":true},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Foo","phosphorilates","bar","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["foo","phosphorilates","bar","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["PROTEIN","O","PROTEIN","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,2,"dobj"],[1,4,"punct"],[2,3,"amod"]],"roots":[1]}]}]}"""
    )
  // TODO: >amod?
  "Odinson TestDocumentationQuantifiers" should "work for '>amod?'" in {
    // get ee
    val ee = this.Utils.mkExtractorEngine(doc)
    // make quuery
    val pattern = """
      trigger = [lemma=bar]
      object: ^NP = >amod?
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val s = ee.query(q)
    s.totalHits shouldEqual (1)
    // check if the token is correct
    s.scoreDocs.head.matches.head.start shouldEqual (2)
    s.scoreDocs.head.matches.head.end shouldEqual (3)
    // test argument
    val desiredArgs = Seq(
      this.createArgument("object", 2, 3)
    )
    this.testEventArguments(s.scoreDocs.head.matches.head, desiredArgs)
    // TODO: why?
    val pattern1 = """
      trigger = [lemma=bar]
      object: ^NP = >amod
    """
    val q1 = ee.compiler.compileEventQuery(pattern1)
    val s1 = ee.query(q1)
    s.totalHits shouldEqual (1)
    // check if the token is correct
    s.scoreDocs.head.matches.head.start shouldEqual (2)
    s.scoreDocs.head.matches.head.end shouldEqual (3)
    // test argument
    val desiredArgs1 = Seq(
      this.createArgument("object", 3, 4)
    )
    this.testEventArguments(s1.scoreDocs.head.matches.head, desiredArgs1)
  }
  // TODO: []*
  // TODO: (>amod [])+
  // TODO: >>{2,3}
}
