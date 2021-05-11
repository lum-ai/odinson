package ai.lum.odinson.foundations

// test imports
import java.nio.file.Files
import ai.lum.odinson.utils.TestUtils.OdinsonTest
import com.typesafe.config.{Config, ConfigValueFactory}
import org.apache.lucene.index.{DirectoryReader, IndexReader}
import org.apache.lucene.store.FSDirectory
// lum imports
import ai.lum.odinson.{OdinsonIndexWriter, DateField, StringField}
import ai.lum.common.ConfigFactory
// file imports
import scala.reflect.io.Directory
import java.io.File

class TestOdinsonIndexWriter extends OdinsonTest {
  type Fixture = OdinsonIndexWriter

  val tmpFolder: File = Files.createTempDirectory("odinson-test").toFile()
  val indexDir = new File(tmpFolder, "index")

  val testConfig: Config = {
    defaultConfig
      // re-compute the index and docs path's
      .withValue(
      "odinson.indexDir",
      ConfigValueFactory.fromAnyRef(indexDir.getAbsolutePath)
    )
  }

  def deleteIndex = {
    val dir = new Directory(indexDir)
    dir.deleteRecursively()
  }

  def getOdinsonIndexWriter: OdinsonIndexWriter = {
    deleteIndex
    OdinsonIndexWriter.fromConfig(testConfig)
  }
  
  "OdinsonIndexWriter" should "object should return index from config correctly" in {
    // get index writer
    val indexWriter = getOdinsonIndexWriter
    // make sure the folder was created with only the locker inside
    indexWriter.directory.listAll.head should be("write.lock")
    indexWriter.close
  }
  
  it should "mkLuceneFields should convert Fields to lucene.Fields correctly" in {
    val indexWriter = getOdinsonIndexWriter
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
  }

  it should "replace invalid characters prior to indexing to prevent off-by-one errors" in {
    val doc = getDocument("bad-character")
    def ee = mkExtractorEngine(doc)

    val pattern = "complex <nsubj phosphorylate >dobj []"
    val expectedMatches = Array("AKT1")

    val query = ee.compiler.mkQuery(pattern)
    val results = ee.query(query, 1)
    results.totalHits should equal (1)

    val matches = results.scoreDocs.head.matches
    val docId = results.scoreDocs.head.doc
    val foundStrings = matches.map(m => ee.getStringForSpan(docId, m))

    foundStrings shouldEqual expectedMatches
  }

  it should "incrementally append to a new index" in {
    val (_, indexDir, config) = getTestConfig()
    val appendConf = config.withValue( "odinson.index.append", ConfigValueFactory.fromAnyRef( true ) )
    val indexer = getOdinsonIndexWriter( appendConf )
    var reader : IndexReader = null

    // add one doc...
    val docOne : OdinsonDocument = getDocument( "alien-species" )
    indexer.addDocument( docOne )

    reader = DirectoryReader.open( FSDirectory.open( indexDir.toPath ) )
    reader.numDocs shouldBe 2
    reader.close()

    val docTwo = getDocument( "ninja-turtles" )
    indexer.addDocument( docTwo )

    reader = DirectoryReader.open( FSDirectory.open( indexDir.toPath ) )
    reader.numDocs() shouldBe 5

    reader.close()
    indexer.close()

    deleteIndex( indexDir )
  }

  it should "incrementally append to an existing index" in {
    val (_, indexDir, config) = getTestConfig()
    val appendConf = config.withValue( "odinson.index.append", ConfigValueFactory.fromAnyRef( true ) )
    var indexer = getOdinsonIndexWriter( appendConf )
    var reader : IndexReader = null

    // add one doc...
    val docOne : OdinsonDocument = getDocument( "alien-species" )
    indexer.addDocument( docOne )

    reader = DirectoryReader.open( FSDirectory.open( indexDir.toPath ) )

    reader.numDocs shouldBe 2
    reader.close()
    indexer.close()

    // reopen the index with a new processor
    indexer = getOdinsonIndexWriter( appendConf )
    reader = DirectoryReader.open( FSDirectory.open( indexDir.toPath ) )

    val docTwo = getDocument( "ninja-turtles" )
    indexer.addDocument( docTwo )

    reader = DirectoryReader.open( FSDirectory.open( indexDir.toPath ) )
    reader.numDocs() shouldBe 5

    reader.close()
    indexer.close()

    deleteIndex(indexDir)
  }

  private def getTestConfig( ) : (File, File, Config) = {
    val tmpFolder : File = Files.createTempDirectory( "odinson-test" ).toFile()
    val indexDir : File = new File( tmpFolder, "index" )
    val testConfig : Config = defaultConfig.withValue( "odinson.indexDir", ConfigValueFactory.fromAnyRef( indexDir.getAbsolutePath ) )
    (tmpFolder, indexDir, testConfig)
  }
}
