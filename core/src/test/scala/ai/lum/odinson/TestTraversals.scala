package ai.lum.odinson

import org.scalatest._

class TestTraversals extends FlatSpec with Matchers {

  val json = """{"id":"a0c553ad-501c-4567-93c3-6a5101c2f5c4","metadata":[],"sentences":[{"numTokens":29,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["In","addition","there","are","alien","species",",","like","red","foxes",",","feral","cats",",","horses","and","cattle",",","which","have","been","introduced","to","Australia","in","the","last","centuries","."],"store":true},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["In","addition","there","are","alien","species",",","like","red","foxes",",","feral","cats",",","horses","and","cattle",",","which","have","been","introduced","to","Australia","in","the","last","centuries","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["IN","NN","EX","VBP","JJ","NNS",",","IN","JJ","NNS",",","JJ","NNS",",","NNS","CC","NNS",",","WDT","VBP","VBN","VBN","TO","NNP","IN","DT","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["in","addition","there","be","alien","species",",","like","red","fox",",","feral","cat",",","horse","and","cattle",",","which","have","be","introduce","to","australium","in","the","last","century","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","I-LOC","O","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-PP","B-NP","B-NP","B-VP","B-NP","I-NP","O","B-PP","B-NP","I-NP","O","B-NP","I-NP","O","B-NP","O","B-NP","O","B-NP","B-VP","I-VP","I-VP","B-PP","B-NP","B-PP","B-NP","I-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"case"],[3,1,"nmod_in"],[3,2,"expl"],[3,5,"nsubj"],[3,9,"nmod_like"],[5,4,"amod"],[9,7,"case"],[9,8,"amod"],[9,12,"appos"],[9,14,"appos"],[9,16,"appos"],[9,18,"ref"],[9,21,"acl:relcl"],[12,11,"amod"],[12,14,"conj_and"],[12,15,"cc"],[12,16,"conj_and"],[21,9,"nsubjpass"],[21,19,"aux"],[21,20,"auxpass"],[21,23,"nmod_to"],[21,27,"nmod_in"],[23,22,"case"],[27,24,"case"],[27,25,"det"],[27,26,"amod"]],"roots":[3]}]}]}"""

  val doc = Document.fromJson(json)
  val ee = TestUtils.mkExtractorEngine(doc)

  "Odinson" should "find all matches across conj_and" in {
    val pattern = "[word=cats] >conj_and [tag=/N.*/]"
    val results = ee.query(pattern, 1)
    results.totalHits should equal (1)
    results.scoreDocs.head.matches should have size 2
    val Array(m1, m2) = results.scoreDocs.head.matches
    ee.getString(m1) should equal ("horses")
    ee.getString(m2) should equal ("cattle")
  }

}
