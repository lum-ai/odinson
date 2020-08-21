package ai.lum.odinson.foundations

// test imports
import org.scalatest._
// lum imports
import ai.lum.odinson.{OdinsonIndexWriter, BaseSpec, DateField, StringField}
import ai.lum.common.ConfigFactory
// file imports
import scala.reflect.io.Directory
import java.io.File

class TestOdinsonIndexWriter extends BaseSpec {
  type Fixture = OdinsonIndexWriter

  def deleteIndexFile =  {
    val config = ConfigFactory.load()
    val indexDir = config.getConfig("odinson").getString("indexDir")
    val file = new File(indexDir)
    val directory = new Directory(file)
    directory.deleteRecursively
  }
  //def getOdinsonIndexWriter()(test: Fixture => Unit): Unit = {
  def getOdinsonIndexWriter = {
    // TODO: can this cause any trouble?
    deleteIndexFile
    OdinsonIndexWriter.fromConfig
  }
  
  "OdinsonIndexWriter" should "object should return index from config correctly" in {
    // get index writer
    val indexWriter = getOdinsonIndexWriter
    // get the directory
    val directory = indexWriter.directory
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
}
