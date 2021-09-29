package ai.lum.odinson.metadata

import ai.lum.odinson.lucene.search.OdinsonFilteredQuery
import ai.lum.odinson.{ Document, ExtractorEngine }
import ai.lum.odinson.metadata.MetadataCompiler.mkQuery
import ai.lum.odinson.test.utils.OdinsonTest
import ai.lum.odinson.utils.exceptions.OdinsonException

class TestMetadataFilter extends OdinsonTest {

  val docs = List(
    // These documents have the sentence: "Becky ate gummy bears."

    // pubdate = 2000-05-25
    // doctype = article
    // citations = 3
    """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[{"$type":"ai.lum.odinson.NumberField","name":"citations","value":3.0},{"$type":"ai.lum.odinson.TokensField","name":"doctype","tokens":["article"]},{"$type":"ai.lum.odinson.DateField","name":"pubdate","date":"2000-05-25"}],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","gummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}""",
    // pubdate = 2005-05-25
    // doctype = website
    """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[{"$type":"ai.lum.odinson.TokensField","name":"doctype","tokens":["website"]},{"$type":"ai.lum.odinson.DateField","name":"pubdate","date":"2005-05-25"}],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","gummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}""",
    // pubdate = 2010-05-25
    // doctype = article
    """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[{"$type":"ai.lum.odinson.TokensField","name":"doctype","tokens":["article"]},{"$type":"ai.lum.odinson.DateField","name":"pubdate","date":"2010-05-25"}],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","gummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}""",
    // pubdate = 2015-05-25
    // doctype = article
    """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[{"$type":"ai.lum.odinson.TokensField","name":"doctype","tokens":["article"]},{"$type":"ai.lum.odinson.DateField","name":"pubdate","date":"2015-05-25"}],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","gummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}""",
    // pubdate = 2015-05-25
    // doctype = website
    """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[{"$type":"ai.lum.odinson.TokensField","name":"doctype","tokens":["website"]},{"$type":"ai.lum.odinson.DateField","name":"pubdate","date":"2015-05-25"}],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","gummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}""",
    // pubdate = 2020-05-25
    // citations = 5
    """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[{"$type":"ai.lum.odinson.NumberField","name":"citations","value":5.0},{"$type":"ai.lum.odinson.DateField","name":"pubdate","date":"2020-05-25"}],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","gummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}""",
    // These documents have the sentence: "Becky ate yummy bears."

    // author = {first: "Agnes", last: "Moorehead"}
    """{"id":"testdoc","metadata":[{"$type":"ai.lum.odinson.NestedField","name":"author","fields":[{"$type":"ai.lum.odinson.TokensField","name":"first","tokens":["Agnes"]},{"$type":"ai.lum.odinson.TokensField","name":"last","tokens":["Moorehead"]}]}],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","yummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","yummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","yummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}""",
    // author = {first: "Agnes", last: "Mertz"}
    // citations = 3
    """{"id":"testdoc","metadata":[{"$type":"ai.lum.odinson.NumberField","name":"citations","value":3.0},{"$type":"ai.lum.odinson.NestedField","name":"author","fields":[{"$type":"ai.lum.odinson.TokensField","name":"first","tokens":["Agnes"]},{"$type":"ai.lum.odinson.TokensField","name":"last","tokens":["Mertz"]}]}],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","yummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","yummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","yummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}""",
    // author = {first: "Jose Manuel", last: "Mertz"}
    """{"id":"testdoc","metadata":[{"$type":"ai.lum.odinson.NumberField","name":"citations","value":3.0},{"$type":"ai.lum.odinson.NestedField","name":"author","fields":[{"$type":"ai.lum.odinson.TokensField","name":"first","tokens":["Jose", "Manuel"]},{"$type":"ai.lum.odinson.TokensField","name":"last","tokens":["Mertz"]}]}],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","yummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","yummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","yummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}""",
    // author = {first: "Sinbad", last: "Valenzuela Escárcega"}
    // citations = 3
    """{"id":"testdoc","metadata":[{"$type":"ai.lum.odinson.NumberField","name":"citations","value":3.0},{"$type":"ai.lum.odinson.NestedField","name":"author","fields":[{"$type":"ai.lum.odinson.TokensField","name":"first","tokens":["Sinbad"]},{"$type":"ai.lum.odinson.TokensField","name":"last","tokens":["Valenzuela", "Escárcega"]}]}],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","yummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","yummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","yummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}""",
    // These documents have the sentence: Becky ate chummy bears

    // keywords: "food", "silly", "outrageous"
    // citations = 3
    """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[{"$type":"ai.lum.odinson.NumberField","name":"citations","value":3.0},{"$type":"ai.lum.odinson.TokensField","name":"keywords","tokens":["food", "silly", "outrageous"]},{"$type":"ai.lum.odinson.DateField","name":"pubdate","date":"2000-05-25"}],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","chummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","chummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","chummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}""",
    // keywords: "food", "games"
    // citations = 3
    """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[{"$type":"ai.lum.odinson.NumberField","name":"citations","value":3.0},{"$type":"ai.lum.odinson.TokensField","name":"keywords","tokens":["food", "games"]},{"$type":"ai.lum.odinson.DateField","name":"pubdate","date":"2000-05-25"}],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","chummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","chummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","chummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}""",
    // keywords: "unknown-words", "silly"
    // citations = 3
    """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[{"$type":"ai.lum.odinson.NumberField","name":"citations","value":3.0},{"$type":"ai.lum.odinson.TokensField","name":"keywords","tokens":["unknown-words", "silly"]},{"$type":"ai.lum.odinson.DateField","name":"pubdate","date":"2000-05-25"}],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","chummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","chummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","chummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}"""
  )

  val ee = ExtractorEngine.inMemory(docs.map(Document.fromJson))
  val query = ee.mkQuery("[word=gummy]")

  behavior of "MetadataFilters"

  it should "not restrict if there are no filters" in {
    ee.query(query).scoreDocs.length shouldBe (6)
  }

  it should "restrict open ended dates" in {
    val filter = mkQuery("pubdate > date(2006, 01, 01)")
    val filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (4)

    val filter2 = mkQuery("date(2006, 01, 01) > pubdate")
    val filteredQuery2 = ee.mkFilteredQuery(query, filter2)
    ee.query(filteredQuery2).scoreDocs.length shouldBe (2)
  }

  it should "restrict by closed date range" in {
    val filter = mkQuery("date(2003, 01, 01) < pubdate < date(2006, 01, 01)")
    val filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (1)
  }

  it should "restrict by exact date" in {
    val filter = mkQuery("pubdate == date(2015, 05, 25)")
    val filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (2)
  }

  it should "handle not equal to date" in {
    val filter = mkQuery("pubdate != date(2015, 05, 25)")
    val filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (4)
  }

  it should "allow dates as strings" in {
    var filter = mkQuery("pubdate == date(2015, 'Mar', 25)")
    var filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (0)

    filter = mkQuery("pubdate == date(2015, 'mARcH', 25)")
    filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (0)

    val filter2 = mkQuery("pubdate == date(2015, 'May', 25)")
    val filteredQuery2 = ee.mkFilteredQuery(query, filter2)
    ee.query(filteredQuery2).scoreDocs.length shouldBe (2)

    val filter3 = mkQuery("pubdate >= date(2015, 'March', 25)")
    val filteredQuery3 = ee.mkFilteredQuery(query, filter3)
    ee.query(filteredQuery3).scoreDocs.length shouldBe (3)
  }

  it should "restrict open ended number ranges" in {
    val filter = mkQuery("citations > 3")
    val filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (1)

    val filter2 = mkQuery("citations >= 3")
    val filteredQuery2 = ee.mkFilteredQuery(query, filter2)
    ee.query(filteredQuery2).scoreDocs.length shouldBe (2)
  }

  it should "restrict by closed number range" in {
    val filter = mkQuery("3 <= citations < 5")
    val filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (1)
  }

  it should "restrict by exact number" in {
    val filter = mkQuery("citations == 5")
    val filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (1)

    val filter2 = mkQuery("citations != 5")
    val filteredQuery2 = ee.mkFilteredQuery(query, filter2)
    ee.query(filteredQuery2).scoreDocs.length shouldBe (5)
  }

  it should "restrict keyword" in {
    val filter = "doctype == 'article'"
    val filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (3)

    val filter2 = "doctype == 'website'"
    val filteredQuery2 = ee.mkFilteredQuery(query, filter2)
    ee.query(filteredQuery2).scoreDocs.length shouldBe (2)
  }

  it should "restrict with AND" in {
    val filter = "doctype == 'article' && (date(1999, 01, 01) < pubdate < date(2012, 01, 01))"
    val filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (2)
  }

  it should "restrict with OR" in {
    val filter = "doctype == 'article' || doctype == 'website'"
    val filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (5)
  }

  it should "restrict by OR and AND" in {
    val filter = "(doctype == 'article' || doctype == 'website') && pubdate < date(2014)"
    val filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (3)
  }

  it should "restrict with negation" in {
    val filter = "!(doctype == 'website')"
    val filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (4)
  }

  it should "restrict with not equal" in {
    val filter = "doctype != 'website'"
    val filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (4)
  }

  // Tests for the nested documents for metadata
  val yummyQuery = ee.mkQuery("[word=yummy]")
  it should "restrict by nested fields" in {
    val filter = "author{first=='Agnes'}"
    val filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (2)
  }

  it should "restrict by nested fields again" in {
    val filter = "author{first=='Agnes' && last=='Moorehead'}"
    val filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (1)
  }

  it should "restrict by nested fields and something else" in {
    val filter = "author{first=='Agnes'} && citations == 3"
    val filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (1)
  }

  it should "restrict with exact match text fields" in {
    var filter = "author{first=='Jose'}"
    var filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (0)

    filter = "author{first=='Jose Manuel'}"
    filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (1)

    filter = "author{first=='Jose Manuel Eduardo'}"
    filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (0)

    filter = "author{first != 'Jose Manuel'}"
    filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (3)
  }

  it should "restrict with contains match text fields" in {

    var filter = "author{first contains 'Jose'}"
    var filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (1)

    filter = "author{first contains 'Jose Manuel'}"
    filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (1)

    filter = "author{first contains 'Jose Manuel Eduardo'}"
    filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (0)

    filter = "author{first not contains 'Jose Manuel Eduardo'}"
    filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (4)

    filter = "author{first not contains 'Jose'}"
    filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (3)

    filter = "author{first contains 'Jose Manuel'}"
    filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (1)

    filter = "author{first contains 'Manuel Jose'}"
    filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (0)

    filter = "author{first not contains 'Agnes'}"
    filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (2)

    filter = "author{first not contains 'Manuel'}"
    filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (3)

    filter = "author{first not contains 'Manuel Jose'}"
    filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (4)

    // case shouldn't matter:
    filter = "author{first contains 'jose'}"
    filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (1)

    // even weird case... :)
    filter = "author{first contains 'jOsE'}"
    filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (1)
  }

  it should "handle unicode in strings" in {
    var filter = "author{last contains 'Valenzuela Escárcega'}"
    var filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (1)

    filter = "author{last contains 'Valenzuela Escarcega'}"
    filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (1)
  }

  // Tests for tokensfields / textfield
  val chummyQuery = ee.mkQuery("[word=chummy]")

  it should "filter against independent strings/tags" in {
    var filter = "keywords contains 'food'"
    var filteredQuery = ee.mkFilteredQuery(chummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (2)

    filter = "keywords contains 'food' && keywords contains 'silly'"
    filteredQuery = ee.mkFilteredQuery(chummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (1)

    // TODO: if/when supported in the language
    // filter = "keywords contains 'food' & 'silly'"

    filter = "keywords contains 'unknown-words' || keywords contains 'silly'"
    filteredQuery = ee.mkFilteredQuery(chummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (2)

  }

  it should "allow for comparison of date attributes" in {
    // year
    var filter = "pubdate.year > 2010"
    var filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (3)

    filter = "pubdate.year == 2020"
    filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (1)

    filter = "2010 < pubdate.year < 2020"
    filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (2)

    filter = "2010 < pubdate.year < 2020 || citations == 3"
    filteredQuery = ee.mkFilteredQuery(query, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (3)

    // add others?
  }

  it should "work with regex tokens" in {
    var filter = "author{first=='/a.*/'}"
    var filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    val f = filteredQuery.asInstanceOf[OdinsonFilteredQuery].filter
    ee.query(filteredQuery).scoreDocs.length shouldBe (2)

    // Should be ok with capitalization too
    filter = "author{first=='/A.*/'}"
    filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (2)

    // should with with multiple terms
    filter = "author{first == 'Jose /Ma.*/'}"
    filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (1)

    filter = "author{first contains '/J.*/ /Ma.*/'}"
    filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (1)

    filter = "author{first=='/A.*/' && last=='/m.*/'}"
    filteredQuery = ee.mkFilteredQuery(yummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (2)

    filter = "keywords contains '/foo./' && keywords contains 'silly'"
    filteredQuery = ee.mkFilteredQuery(chummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (1)
  }

  "Metadata" should "work when in grammars as a string" in {
    ee.clearState()
    val rules = """
      |metadataFilters: doctype == 'article'
      |
      |vars:
      |  chunk: "[chunk=B-NP][chunk=I-NP]*"
      |
      |rules:
      |  - name: testrule
      |    type: event
      |    label: Test
      |    pattern: |
      |      trigger = [lemma=eat]
      |      subject: ^NP = >nsubj ${chunk}
      |      object: ^NP = >dobj ${chunk}
    """.stripMargin
    val extractors = ee.ruleReader.compileRuleString(rules)
    val mentions = getMentionsWithLabel(ee.extractMentions(extractors).toSeq, "Test")
    mentions should have size (3)

  }

  "Metadata" should "work when in grammars as a list" in {
    ee.clearState()
    val rules = """
      |metadataFilters:
      |  - doctype == 'article'
      |  - (date(1999, 01, 01) < pubdate < date(2012, 01, 01))
      |
      |vars:
      |  chunk: "[chunk=B-NP][chunk=I-NP]*"
      |
      |rules:
      |  - name: testrule
      |    type: event
      |    label: Test
      |    pattern: |
      |      trigger = [lemma=eat]
      |      subject: ^NP = >nsubj ${chunk}
      |      object: ^NP = >dobj ${chunk}
    """.stripMargin
    val extractors = ee.ruleReader.compileRuleString(rules)
    val mentions = getMentionsWithLabel(ee.extractMentions(extractors).toSeq, "Test")
    mentions should have size (2)

  }

  "Metadata" should "combine properly when files are imported" in {
    ee.clearState()
    val masterPath = "/testMetadataImports/master.yml"
    val extractors = ee.compileRuleResource(masterPath)
    val mentions = getMentionsWithLabel(ee.extractMentions(extractors).toSeq, "Test")
    mentions should have size (2)

  }

  "Metadata" should "combine properly when files are imported with filter" in {
    ee.clearState()
    val masterPath = "/testMetadataImports/master2.yml"
    val extractors = ee.compileRuleResource(masterPath)
    val mentions = getMentionsWithLabel(ee.extractMentions(extractors).toSeq, "Test")
    mentions should have size (2)

  }

  "Metadata" should "does not work with StringFields but does with Tokens Fields" in {
    // based on issue #328
    val docJson =
      """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[
        |  {
        |    "$type": "ai.lum.odinson.StringField",
        |    "name": "file",
        |    "string": "AAT"
        |  },
        |  {
        |    "$type": "ai.lum.odinson.TokensField",
        |    "name": "corpus",
        |    "tokens": ["BNC"]
        |  }
        |],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","chummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","chummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","chummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}""".stripMargin

    val localEe = ExtractorEngine.inMemory(Document.fromJson(docJson))
    val localQuery = localEe.mkQuery("[word=chummy]")
    localEe.query(localQuery).scoreDocs.length shouldBe (1)

    // TokensField metadata is supported
    val filter = "corpus=='BNC'"
    val filteredQuery = localEe.mkFilteredQuery(localQuery, filter)
    localEe.query(filteredQuery).scoreDocs.length shouldBe (1)

    // The StringField metadata isn't queryable (at this time)
    val filterInvalid = "file=='AAT'"
    val filteredQueryInvalid = localEe.mkFilteredQuery(localQuery, filterInvalid)
    localEe.query(filteredQueryInvalid).scoreDocs.length shouldBe (0)
  }

}
