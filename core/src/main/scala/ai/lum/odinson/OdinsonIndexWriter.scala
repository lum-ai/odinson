package ai.lum.odinson

import java.io.File
import java.nio.file.Path
import java.util.Collection

import scala.collection.JavaConverters._
import org.apache.lucene.document.Document
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.index.{IndexWriter, IndexWriterConfig}
import org.apache.lucene.index.IndexWriterConfig.OpenMode
import org.apache.lucene.store.{Directory, FSDirectory, RAMDirectory}
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

  def commit(): Unit = {
    writer.commit()
  }

  def close(file: File): Unit = {
    vocabulary.dumpToFile(file)
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

  def apply(directory: Directory, vocabulary: Vocabulary): OdinsonIndexWriter = new OdinsonIndexWriter(directory, vocabulary)

  def apply(indexDir: File, vocabularyFile: File): OdinsonIndexWriter = {
    val dir = FSDirectory.open(indexDir.toPath)
    // dependencies vocabulary
    val vocabulary = Vocabulary.empty
    OdinsonIndexWriter(dir, vocabulary)
  }

  def apply(config: Config): OdinsonIndexWriter = {
    val indexDir   = config[Path]("indexDir")
    val directory  = FSDirectory.open(indexDir)
    val vocabFile  = config[File]("odinson.compiler.dependenciesVocabulary")
    val vocabulary = if (vocabFile.exists) {
      Vocabulary.load(vocabFile)
    } else Vocabulary.empty
    OdinsonIndexWriter(directory, vocabulary)
  }

  def fromConfig: OdinsonIndexWriter = {
    val config     = ConfigFactory.load()
    OdinsonIndexWriter(config)
  }

  def inMemory: OdinsonIndexWriter = OdinsonIndexWriter(new RAMDirectory(), Vocabulary.empty)
}