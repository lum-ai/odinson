package ai.lum.odinson

import java.io.File
import java.nio.file.Path
import java.util.Collection

import scala.collection.JavaConverters._
import org.apache.lucene.document.Document
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.index.{IndexWriter, IndexWriterConfig}
import org.apache.lucene.index.IndexWriterConfig.OpenMode
import org.apache.lucene.store.{Directory, FSDirectory, IOContext, RAMDirectory}
import ai.lum.common.ConfigUtils._
import ai.lum.odinson.digraph.{DirectedGraph, Vocabulary}
import ai.lum.odinson.utils.ConfigFactory
import com.typesafe.config.Config

class OdinsonIndexWriter(
  val directory: Directory,
  val vocabulary: Vocabulary
) {

  val analyzer = new WhitespaceAnalyzer()
  val writerConfig = new IndexWriterConfig(analyzer)
  writerConfig.setOpenMode(OpenMode.CREATE)
  val writer = new IndexWriter(directory, writerConfig)

  def addDocuments(block: Seq[Document]): Unit = {
    addDocuments(block.asJava)
  }

  def addDocuments(block: Collection[Document]): Unit = {
    writer.addDocuments(block)
  }

  def commit(): Unit = writer.commit()

  def close(): Unit = {
    // FIXME: is this the correct instantiation of IOContext?
    val stream = directory.createOutput(Vocabulary.FILE_NAME, new IOContext)
    stream.writeString(vocabulary.dump)
    stream.close()
    writer.close()
  }

  def mkDirectedGraph(
    incomingEdges: Array[Array[(Int, String)]],
    outgoingEdges: Array[Array[(Int, String)]],
    roots: Array[Int]
  ): DirectedGraph = {
    def toLabelIds(tokenEdges: Array[(Int, String)]): Array[Int] = for {
      (tok, label) <- tokenEdges
      labelId = vocabulary.getOrCreateId(label)
      n <- Array(tok, labelId)
    } yield n
    val incoming = incomingEdges.map(toLabelIds)
    val outgoing = outgoingEdges.map(toLabelIds)
    DirectedGraph(incoming, outgoing, roots)
  }

}


object OdinsonIndexWriter {

  def apply(directory: Directory, vocabulary: Vocabulary): OdinsonIndexWriter = {
    new OdinsonIndexWriter(directory, vocabulary)
  }

  def apply(indexDir: File): OdinsonIndexWriter = {
    OdinsonIndexWriter(indexDir.toPath)
  }

  def apply(indexDir: Path): OdinsonIndexWriter = {
    val directory  = FSDirectory.open(indexDir)
    val vocabulary = Vocabulary.fromIndex(directory)
    OdinsonIndexWriter(directory, vocabulary)
  }

  def fromConfig(): OdinsonIndexWriter = {
    fromConfig("odinson")
  }

  def fromConfig(path: String): OdinsonIndexWriter = {
    val config = ConfigFactory.load()
    fromConfig(config[Config](path))
  }

  def fromConfig(config: Config): OdinsonIndexWriter = {
    val indexDir = config[Path]("indexDir")
    OdinsonIndexWriter(indexDir)
  }

  def inMemory: OdinsonIndexWriter = {
    OdinsonIndexWriter(new RAMDirectory(), Vocabulary.empty)
  }

}
