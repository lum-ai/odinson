package ai.lum.odinson.metadata

import ai.lum.odinson.lucene.search.OdinsonFilteredQuery
import ai.lum.odinson.{ Document, ExtractorEngine }
import ai.lum.odinson.utils.TestUtils.OdinsonTest
import ai.lum.odinson.metadata.MetadataCompiler.mkQuery

class TestMetadataFilter extends OdinsonTest {

  val docs = List(
    // First 6 documents have text: "Becky ate gummy bears."

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
    // These two documents have the sentence: "Becky ate yummy bears."

    // author = {first: "Agnes", last: "Moorehead"}
    """{"id":"testdoc","metadata":[{"$type":"ai.lum.odinson.NestedField","name":"author","fields":[{"$type":"ai.lum.odinson.TokensField","name":"first","tokens":["Agnes"]},{"$type":"ai.lum.odinson.TokensField","name":"last","tokens":["Moorehead"]}]}],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","yummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","yummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","yummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}""",
    // author = {first: "Agnes", last: "Mertz"}
    // citations = 3
    """{"id":"testdoc","metadata":[{"$type":"ai.lum.odinson.NumberField","name":"citations","value":3.0},{"$type":"ai.lum.odinson.NestedField","name":"author","fields":[{"$type":"ai.lum.odinson.TokensField","name":"first","tokens":["Agnes"]},{"$type":"ai.lum.odinson.TokensField","name":"last","tokens":["Mertz"]}]}],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","yummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","yummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","yummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}""",
    // author = {first: "Jose Manuel", last: "Mertz"}
    """{"id":"testdoc","metadata":[{"$type":"ai.lum.odinson.NumberField","name":"citations","value":3.0},{"$type":"ai.lum.odinson.NestedField","name":"author","fields":[{"$type":"ai.lum.odinson.TokensField","name":"first","tokens":["Jose", "Manuel"]},{"$type":"ai.lum.odinson.TokensField","name":"last","tokens":["Mertz"]}]}],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","yummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","yummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","yummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}""",
    // todo author = {first: "Agnes", last: "Valenzuela Escárcega"}
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

    // todo: if/when supported in the language
    // filter = "'food' & 'silly' in keywords"

    filter = "keywords contains 'unknown-words' || keywords contains 'silly'"
    filteredQuery = ee.mkFilteredQuery(chummyQuery, filter)
    ee.query(filteredQuery).scoreDocs.length shouldBe (2)

  }

  // todo: syntactic sugar for dates

}
