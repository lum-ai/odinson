package ai.lum.odinson.extra

import java.io.File

import ai.lum.common.ConfigFactory
import ai.lum.common.ConfigUtils._
import ai.lum.common.FileUtils._
import ai.lum.odinson.OdinsonIndexWriter
import ai.lum.odinson.extra.IndexDocuments.indexDocuments
import org.clulab.processors.fastnlp.FastNLPProcessor

object ExampleBuildIndex extends App {

  // --------------------------
  //        Data paths
  // --------------------------

  val config = ConfigFactory.load()
  // The location of the text files
  val textDir: File = config[File]("odinson.textDir")
  // The place where annotated Document json files will be stored
  val docsDir: File = config[File]("odinson.docsDir")

  // --------------------------
  //  Annotate the text files
  // --------------------------

  // This is an NLP processor from CluLab processors (https://github.com/clulab/processors),
  // but text can be annotated with any tool the user wants.
  val processor = new FastNLPProcessor()

  // Get the files to be processed
  val textFiles = textDir.listFilesByWildcard("*.txt", recursive = true)

  // NOTE parses the documents in parallel
  for (file <- textFiles.par) {
    // Make a new file for storing the annotation of the current text file
    val docFile = new File(docsDir, file.getBaseName() + ".json.gz")
    val text = file.readString()
    // Perform NLP annotation, included here are: sentence splitting, tokenization,
    // POS tagging, NER, lemmatization, syntactic parsing.
    val doc = processor.annotate(text)
    // Use file base name as document id
    doc.id = Some(file.getBaseName())
    // Convert to an Odinson Document
    val odinsonDocument = ProcessorsUtils.convertDocument(doc)
    // Export
    docFile.writeString(odinsonDocument.toJson)
  }

  // --------------------------
  //      Make the Index
  // --------------------------

  // Initialize the IndexWriter
  val writer = OdinsonIndexWriter.fromConfig()
  // Parallel list of Documents made above
  val documentFiles = docsDir.listFilesByWildcard("*.json.gz", recursive = true).par
  println("Indexing documents")
  // Build the index
  indexDocuments(writer, documentFiles)
  writer.close()
}
