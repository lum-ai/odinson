package ai.lum.odinson.metadata

import ai.lum.odinson.lucene.search.OdinsonFilteredQuery
import ai.lum.odinson.{Document, ExtractorEngine}
import ai.lum.odinson.utils.TestUtils.OdinsonTest
import ai.lum.odinson.metadata.MetadataCompiler.mkQuery

class TestMetadataFilter extends OdinsonTest {


  // First 6 documents have text: "Becky ate gummy bears."

  // pubdate = 2000-05-25
  // doctype = article
  // citations = 3
  val doc1 = """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[{"$type":"ai.lum.odinson.NumberField","name":"citations","value":3.0},{"$type":"ai.lum.odinson.TokensField","name":"doctype","tokens":["article"]},{"$type":"ai.lum.odinson.DateField","name":"pubdate","date":"2000-05-25"}],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","gummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}"""
  // pubdate = 2005-05-25
  // doctype = website
  val doc2 = """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[{"$type":"ai.lum.odinson.TokensField","name":"doctype","tokens":["website"]},{"$type":"ai.lum.odinson.DateField","name":"pubdate","date":"2005-05-25"}],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","gummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}"""
  // pubdate = 2010-05-25
  // doctype = article
  val doc3 = """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[{"$type":"ai.lum.odinson.TokensField","name":"doctype","tokens":["article"]},{"$type":"ai.lum.odinson.DateField","name":"pubdate","date":"2010-05-25"}],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","gummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}"""
  // pubdate = 2015-05-25
  // doctype = article
  val doc4 = """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[{"$type":"ai.lum.odinson.TokensField","name":"doctype","tokens":["article"]},{"$type":"ai.lum.odinson.DateField","name":"pubdate","date":"2015-05-25"}],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","gummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}"""
  // pubdate = 2015-05-25
  // doctype = website
  val doc5 = """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[{"$type":"ai.lum.odinson.TokensField","name":"doctype","tokens":["website"]},{"$type":"ai.lum.odinson.DateField","name":"pubdate","date":"2015-05-25"}],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","gummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}"""
  // pubdate = 2020-05-25
  // citations = 5
  val doc6 = """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[{"$type":"ai.lum.odinson.NumberField","name":"citations","value":5.0},{"$type":"ai.lum.odinson.DateField","name":"pubdate","date":"2020-05-25"}],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","gummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}"""

  // These two documents have the sentence: "Becky ate yummy bears."

  // author = {first: "Agnes", last: "Moorehead"}
  val doc7 = """{"id":"testdoc","metadata":[{"$type":"ai.lum.odinson.NestedField","name":"author","fields":[{"$type":"ai.lum.odinson.TokensField","name":"first","tokens":["Agnes"]},{"$type":"ai.lum.odinson.TokensField","name":"last","tokens":["Moorehead"]}]}],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","yummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","yummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","yummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}"""
  // author = {first: "Agnes", last: "Mertz"}
  // citations = 3
  val doc8 = """{"id":"testdoc","metadata":[{"$type":"ai.lum.odinson.NumberField","name":"citations","value":3.0},{"$type":"ai.lum.odinson.NestedField","name":"author","fields":[{"$type":"ai.lum.odinson.TokensField","name":"first","tokens":["Agnes"]},{"$type":"ai.lum.odinson.TokensField","name":"last","tokens":["Mertz"]}]}],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","yummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","yummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","yummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}"""
  // author = {first: "Jose Manuel", last: "Mertz"}
  val doc9 = """{"id":"testdoc","metadata":[{"$type":"ai.lum.odinson.NumberField","name":"citations","value":3.0},{"$type":"ai.lum.odinson.NestedField","name":"author","fields":[{"$type":"ai.lum.odinson.TokensField","name":"first","tokens":["Jose", "Manuel"]},{"$type":"ai.lum.odinson.TokensField","name":"last","tokens":["Mertz"]}]}],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","yummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","yummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","yummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}"""
  // todo author = {first: "Agnes", last: "Valenzuela Escárcega"}


  val ee = ExtractorEngine.inMemory(Seq(doc1, doc2, doc3, doc4, doc5, doc6, doc7, doc8, doc9).map(Document.fromJson))
  val query = ee.compiler.compile("[word=gummy]")

  behavior of "MetadataFilters"

  it should "not restrict if there are no filters" in {
    ee.query(query).scoreDocs.length shouldBe(6)
  }

  it should "restrict open ended dates" in {
    val filter = mkQuery("pubdate > date(2006, 01, 01)")
    val filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe(4)

    val filter2 = mkQuery("date(2006, 01, 01) > pubdate")
    val filteredQuery2 = ee.mkFilteredQuery(query, filter2)
    ee.query(filteredQuery2).scoreDocs.length shouldBe(2)
  }

  it should "restrict by closed date range" in {
    val filter = mkQuery("date(2003, 01, 01) < pubdate < date(2006, 01, 01)")
    val filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe(1)
  }

  it should "restrict by exact date" in {
    val filter = mkQuery("pubdate == date(2015, 05, 25)")
    val filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe(2)
  }

  it should "handle not equal to date" in {
    val filter = mkQuery("pubdate != date(2015, 05, 25)")
    val filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe(4)
  }

  it should "allow dates as strings" in {
    val filter = mkQuery("pubdate == date(2015, 'Mar', 25)")
    val filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe(0)

    val filter2 = mkQuery("pubdate == date(2015, 'May', 25)")
    val filteredQuery2 = ee.mkFilteredQuery(query, filter2)
    ee.query(filteredQuery2).scoreDocs.length shouldBe(2)

    val filter3 = mkQuery("pubdate >= date(2015, 'March', 25)")
    val filteredQuery3 = ee.mkFilteredQuery(query, filter3)
    ee.query(filteredQuery3).scoreDocs.length shouldBe(3)
  }

  it should "restrict open ended number ranges" in {
    val filter = mkQuery("citations > 3")
    val filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe(1)

    val filter2 = mkQuery("citations >= 3")
    val filteredQuery2 = ee.mkFilteredQuery(query, filter2)
    ee.query(filteredQuery2).scoreDocs.length shouldBe(2)
  }

  it should "restrict by closed number range" in {
    val filter = mkQuery("3 <= citations < 5")
    val filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe(1)
  }

  it should "restrict by exact number" in {
    val filter = mkQuery("citations == 5")
    val filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe(1)

    val filter2 = mkQuery("citations != 5")
    val filteredQuery2 = ee.mkFilteredQuery(query, filter2)
    ee.query(filteredQuery2).scoreDocs.length shouldBe(5)
  }

  it should "restrict keyword" in {
    val filter = "doctype == 'article'"
    val filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe(3)

    val filter2 = "doctype == 'website'"
    val filteredQuery2 = ee.mkFilteredQuery(query, filter2)
    ee.query(filteredQuery2).scoreDocs.length shouldBe(2)
  }

  it should "restrict with AND" in {
    val filter = "doctype == 'article' && (date(1999, 01, 01) < pubdate < date(2012, 01, 01))"
    val filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe(2)
  }

  it should "restrict with OR" in {
    val filter = "doctype == 'article' || doctype == 'website'"
    val filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe(5)
  }

  it should "restrict by OR and AND" in {
    val filter = "(doctype == 'article' || doctype == 'website') && pubdate < date(2014)"
    val filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe(3)
  }

  it should "restrict with negation" in {
    val filter = "!(doctype == 'website')"
    val filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe(4)
  }

  it should "restrict with not equal" in {
    val filter = "doctype != 'website'"
    val filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe(4)
  }

  // Tests for the nested documents for metadata
  val yummyQuery = ee.compiler.compile("[word=yummy]")
  it should "restrict by nested fields" in {
    val filter = "author{first=='Agnes'}"
    val filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe(2)
  }

  it should "restrict by nested fields again" in {
    val filter = "author{first=='Agnes' && last=='Moorehead'}"
    val filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe(1)
  }

  it should "restrict by nested fields and something else" in {
    val filter = "author{first=='Agnes'} && citations == 3"
    val filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe(1)
  }

  // todo: unicode norm in the metadata queries


  it should "restrict with exact match text fields" in {
    var filter = "author{first=='Jose'}"
    var filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe(0)

    filter = "author{first=='Jose Manuel'}"
    filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe(1)

    filter = "author{first=='Jose Manuel Eduardo'}"
    filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe(0)

    filter = "author{first != 'Jose Manuel'}"
    filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe(2)
  }

  it should "restrict with contains match text fields" in {
    
    var filter = "author{'Jose' in first}"
    var filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe(1)

    filter = "author{'Jose Manuel' in first}"
    filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe(1)

    filter = "author{'Jose Manuel Eduardo' in first}"
    filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe(0)

    filter = "author{'Jose Manuel Eduardo' not in first}"
    filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe(3)

    filter = "author{'Jose' not in first}"
    filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe(2)

    filter = "author{'Jose Manuel' in first}"
    filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe(1)

    filter = "author{'Agnes' not in first}"
    filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe(1)

    filter = "author{'Manuel' not in first}"
    filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe(2)
  }


  // journal: "New American Entomology"
  // j: "American Biology"
  // TF("journal", ["American", "Biology"])


  // 'American Entomology' in journal ==> part of a string
  // new amer ent



  // need to support PhraseQuery --> Valenzuela Escárcega vs Escárcega Valenzuela, fool!

  // "keywords == 'article'"
  // "'article' in keywords" ==> match the entire string


  // keywords = ["article", "Peru", "important", "covid"]
  // is this a StringField? (i.e., multiple strings but none are tokenized)
  // 'Peru' in keywords && 'covid' in keywords
  // 'Peru' & 'covid' in keywords <-- junctions


}
