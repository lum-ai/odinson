package ai.lum.odinson.foundations


// test imports
import org.scalatest._
// lum imports
import ai.lum.odinson.{OdinsonIndexWriter, BaseSpec}
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
  

// TODO: test mkMedatadaDoc(Document) lucenedoc.Document
// TODO: test mkLucenefileds serialized dependencies too big for storage
// TODO: test mkLuceneFields(Fields) Seq(Lucenedoc.Field)

}
