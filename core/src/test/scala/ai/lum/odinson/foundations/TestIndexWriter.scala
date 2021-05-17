package ai.lum.odinson.foundations

import java.io.File
import java.nio.file.Files

import com.typesafe.config.{Config, ConfigValueFactory}

// lum imports
import ai.lum.odinson._
import ai.lum.odinson.serialization.UnsafeSerializer
import ai.lum.odinson.utils.IndexSettings
import ai.lum.odinson.utils.TestUtils.OdinsonTest
import ai.lum.odinson.utils.exceptions.OdinsonException
import ai.lum.odinson.OdinsonIndexWriter
import ai.lum.odinson.{Document => OdinsonDocument}

import org.apache.lucene.index.{DirectoryReader, IndexReader}
import org.apache.lucene.store.FSDirectory
import org.scalatest.BeforeAndAfterEach


import scala.reflect.io.Directory
import scala.collection.JavaConverters._

class TestOdinsonIndexWriter extends OdinsonTest with BeforeAndAfterEach {
  type Fixture = OdinsonIndexWriter

  val tmpFolder: File = Files.createTempDirectory("odinson-test").toFile()
  val indexDir = new File(tmpFolder, "index")

  val testConfig: Config = {
    defaultConfig
      // re-compute the index and docs path's
      .withValue("odinson.indexDir", ConfigValueFactory.fromAnyRef(indexDir.getAbsolutePath))
  }

  override def beforeEach( ) : Unit = deleteIndex()

  def deleteIndex() : Unit = {
    val index = new Directory(indexDir)
    if(index.exists)index.deleteRecursively()
  }

  def getOdinsonIndexWriter(config : Config = testConfig): OdinsonIndexWriter = {
    OdinsonIndexWriter.fromConfig(config)
  }

  "OdinsonIndexWriter" should "object should return index from config correctly" in {
    // get index writer
    val indexWriter = getOdinsonIndexWriter()
    // make sure the folder was created with only the locker inside
    indexWriter.directory.listAll.head should be("write.lock")
    indexWriter.close
  }

  it should "mkLuceneFields should convert Fields to lucene.Fields correctly" in {
    val indexWriter = getOdinsonIndexWriter()
    // Initialize fild of type DateField
    var field =
      """{"$type":"ai.lum.odinson.DateField","name":"smth","date":"1993-03-28"}"""
    //
    val dateField = DateField.fromJson(field)
    // DateField
    val luceneDateField = indexWriter.mkLuceneFields(dateField)
    // test
    luceneDateField.head.name shouldEqual ("smth")
    // Initialize field of type StringField
    field =
      """{"$type":"ai.lum.odinson.StringField","name":"smth","string":"foo"}"""
    // StringField
    val stringField = StringField.fromJson(field)
    val luceneStringField = indexWriter.mkLuceneFields(stringField)
    luceneStringField.head.name shouldEqual ("smth")
    // TODO: should we test more stuff
    indexWriter.close()
  }

  it should "incrementally append to a new index" in {
    val appendConf = testConfig.withValue("odinson.index.incremental", ConfigValueFactory.fromAnyRef(true))
    val indexer = getOdinsonIndexWriter(appendConf)
    var reader : IndexReader = null

    // add one doc...
    val docOne : OdinsonDocument = getDocument("alien-species")
    indexer.addDocument(docOne)

    reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath))
    reader.numDocs shouldBe 2 // number of lucene entries, not documents
    reader.close()

    val docTwo = getDocument("ninja-turtles")
    indexer.addDocument(docTwo)

    reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath))
    reader.numDocs() shouldBe 5 // number of lucene entries, not documents

    reader.close()
    indexer.close()
  }

  it should "incrementally append to an existing index" in {
    val appendConf = testConfig.withValue("odinson.index.incremental", ConfigValueFactory.fromAnyRef(true))
    var indexer : OdinsonIndexWriter = getOdinsonIndexWriter(appendConf)
    var reader : IndexReader = null

    // add one doc...
    val docOne : OdinsonDocument = getDocument("alien-species")
    indexer.addDocument(docOne)

    reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath))

    reader.numDocs shouldBe 2 // number of lucene entries, not documents
    reader.close()
    indexer.close()

    // reopen the index...
    indexer = getOdinsonIndexWriter(appendConf)
    reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath))

    val docTwo = getDocument("ninja-turtles")
    indexer.addDocument(docTwo)

    reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath))
    reader.numDocs() shouldBe 5 // number of lucene entries, not documents

    reader.close()
    indexer.close()
  }

  it should "replace invalid characters prior to indexing to prevent off-by-one errors" in {
    val doc = getDocument("bad-character")
    def ee = mkExtractorEngine(doc)

    val pattern = "complex <nsubj phosphorylate >dobj []"
    val expectedMatches = Array("AKT1")

    val query = ee.compiler.mkQuery(pattern)
    val results = ee.query(query, 1)
    results.totalHits should equal(1)

    val matches = results.scoreDocs.head.matches
    val docId = results.scoreDocs.head.doc
    val foundStrings = matches.map(m => ee.getStringForSpan(docId, m))

    foundStrings shouldEqual expectedMatches
  }

  it should "properly dump and load relevant settings" in {
    val indexFile = new File(tmpFolder, "index2")
    val customConfig: Config = {
      testConfig
        // re-compute the index and docs path's
        .withValue("odinson.indexDir", ConfigValueFactory.fromAnyRef(indexFile.getAbsolutePath))
        .withValue(
          "odinson.index.storedFields",
          ConfigValueFactory.fromAnyRef(Seq("apple", "banana", "kiwi", "raw").asJava)
        )
    }

    val indexWriter = OdinsonIndexWriter.fromConfig(customConfig)
    // close and write the settings file
    indexWriter.close()
    val settings = IndexSettings.fromDirectory(FSDirectory.open(indexFile.toPath))
    settings.storedFields should contain theSameElementsAs Seq(
      "apple",
      "banana",
      "kiwi",
      indexWriter.displayField
    )
  }

  it should "store stored fields and not others" in {

    val doc = getDocument("rainbows")
    val customConfig: Config = defaultConfig
      .withValue(
        "odinson.index.storedFields",
        ConfigValueFactory.fromAnyRef(Seq("tag", "raw").asJava)
      )
    def ee = mkExtractorEngine(customConfig, doc)

    // we asked it to store `tag` so the extractor engine should be able to access the content
    ee.getTokensForSpan(0, "tag", 0, 1) should contain only "NNS"
    // though `entity` is a field in the Document, it wasn't stored, so the extractor engine shouldn't
    // be able to retrieve the content
    an[OdinsonException] should be thrownBy ee.getTokensForSpan(0, "entity", 0, 1)

  }

  it should "throw an exception if the displayField isn't in the storedFields" in {
    val indexFile = new File(tmpFolder, "index2")
    val customConfig: Config = {
      testConfig
        // re-compute the index and docs path's
        .withValue("odinson.indexDir", ConfigValueFactory.fromAnyRef(indexFile.getAbsolutePath))
        .withValue(
          "odinson.index.storedFields",
          ConfigValueFactory.fromAnyRef(Seq("apple", "banana", "kiwi").asJava)
        )
    }

    an[OdinsonException] shouldBe thrownBy { OdinsonIndexWriter.fromConfig(customConfig) }
  }

  it should "store and retrieve large graphs" in {

    val numTokens = 4000
    val large = Array.fill[String](numTokens)("test")
    large(0) = "start"
    large(3) = "success"
    val raw = TokensField("raw", large.toSeq)
    val word = TokensField("word", large.toSeq)

    val deps = new Array[(Int, Int, String)](numTokens)
    for (i <- 0 until numTokens - 1) {
      if (i < 3) {
        // (0,1,edge)
        deps(i) = (i, i + 1, "edge")
      } else {
        deps(i) = (i, i + 1, "nomatch")
      }
    }
    val roots = Set[Int](0)
    val graph = GraphField("dependencies", deps, roots)

    val sent = Sentence(numTokens, Seq(raw, word, graph))
    val doc = Document("testdoc", Seq.empty, Seq(sent))

    // Yes, in fact the deps field is above the previous threshold
    val incomingEdges = graph.mkIncomingEdges(numTokens)
    val outgoingEdges = graph.mkOutgoingEdges(numTokens)
    val indexWriter = getOdinsonIndexWriter()

    val directedGraph = indexWriter.mkDirectedGraph(incomingEdges, outgoingEdges, roots.toArray)
    val bytes = UnsafeSerializer.graphToBytes(directedGraph)
    // previously, we only supported up to this: sortedDocValuesFieldMaxSize = 32766
    bytes.length should be > (32766)
    indexWriter.close()

    // ensure the EE can use it
    val ee =
      extractorEngineWithConfigValue(doc, "odinson.index.maxNumberOfTokensPerSentence", numTokens)
    val rules =
      """
        |rules:
        |  - name: testrule
        |    type: basic
        |    label: Test
        |    pattern: |
        |      start >edge+ success
        |""".stripMargin
    val extractors = ee.compileRuleString(rules)
    val results = ee.extractNoState(extractors).toArray
    results.length should be(1)
    ee.getTokensForSpan(results.head) should contain only "success"
  }
}
