package ai.lum.odinson.documentation

object DocumentationDocs {

  val json = Map(
    // She saw me and Julio
    "me_and_julio" -> """{"id":"4e18f7d1-8ef4-4f7a-8906-4a94b98acb3e","metadata":[],"sentences":[{"numTokens":6,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["She","saw","me","and","Julio","."]},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["She","saw","me","and","Julio","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["PRP","VBD","PRP","CC","NNP","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["she","see","I","and","Julio","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["O","O","O","O","PERSON","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","O","B-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,2,"dobj"],[1,4,"dobj"],[1,5,"punct"],[2,3,"cc"],[2,4,"conj_and"]],"roots":[1]}]}]}"""
  )

}
