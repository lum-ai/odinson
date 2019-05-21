package ai.lum.odinson.extra

import java.io._
import scala.collection.mutable.ArrayBuffer
import org.apache.lucene.util.BytesRef
import org.apache.lucene.document._
import org.apache.lucene.document.Field.Store
import org.clulab.processors.{ Sentence, Document => ProcessorsDocument }
import org.clulab.serialization.json.JSONSerializer
import org.json4s._
import org.json4s.jackson.JsonMethods._
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.config.ConfigFactory
import ai.lum.common.ConfigUtils._
import ai.lum.common.FileUtils._
import ai.lum.common.Serializer
import ai.lum.labrador.DocumentMetadata
import ai.lum.odinson.lucene.analysis._
import ai.lum.odinson.OdinsonIndexWriter

import scala.util.{ Failure, Success, Try }


object IndexDocuments extends App with LazyLogging {

  val config = ConfigFactory.load()
  val indexDir = config[File]("odinson.indexDir")
  val docsDir  = config[File]("odinson.docsDir")
  val documentIdField = config[String]("odinson.index.documentIdField")
  val sentenceIdField = config[String]("odinson.index.sentenceIdField")
  val sentenceLengthField  = config[String]("odinson.index.sentenceLengthField")
  val rawTokenField        = config[String]("odinson.index.rawTokenField")
  val wordTokenField       = config[String]("odinson.index.wordTokenField")
  val normalizedTokenField = config[String]("odinson.index.normalizedTokenField")
  val lemmaTokenField      = config[String]("odinson.index.lemmaTokenField")
  val posTagTokenField     = config[String]("odinson.index.posTagTokenField")
  val chunkTokenField      = config[String]("odinson.index.chunkTokenField")
  val entityTokenField     = config[String]("odinson.index.entityTokenField")
  val incomingTokenField   = config[String]("odinson.index.incomingTokenField")
  val outgoingTokenField   = config[String]("odinson.index.outgoingTokenField")
  val dependenciesField    = config[String]("odinson.index.dependenciesField")
  val sortedDocValuesFieldMaxSize  = config[Int]("odinson.index.sortedDocValuesFieldMaxSize")
  val maxNumberOfTokensPerSentence = config[Int]("odinson.index.maxNumberOfTokensPerSentence")

  val storeSentenceJson   = config[Boolean]("odinson.extra.storeSentenceJson")

  implicit val formats = DefaultFormats

  val writer = OdinsonIndexWriter.fromConfig

  // serialized org.clulab.processors.Document or Document json
  val SUPPORTED_EXTENSIONS = "(?i).*?\\.(ser|json)$"
  // NOTE indexes the documents in parallel
  // FIXME: groupBy by extension-less basename?
  for {
    f <- docsDir.listFilesByRegex(SUPPORTED_EXTENSIONS, recursive = true).toSeq.par
    if ! f.getName.endsWith(".metadata.ser")
  } {
    Try {
      val (doc, md) = deserializeDoc(f)
      val block = mkDocumentBlock(doc, md)
      writer.addDocuments(block)
    } match {
      case Success(_) =>
        logger.info(s"Indexed ${f.getName}")
      case Failure(e) =>
        logger.error(s"Failed to index ${f.getName}", e)
    }

  }

  writer.close

  // fin


  def deserializeDoc(f: File): (ProcessorsDocument, Option[DocumentMetadata]) = f.getName.toLowerCase match {
    case json if json.endsWith(".json") =>
      val doc = JSONSerializer.toDocument(f)
      (doc, None)
    case ser if ser.endsWith(".ser") =>
      val doc = Serializer.deserialize[ProcessorsDocument](f)
      val md: Option[DocumentMetadata] = {
        val mdFile = new File(f.getCanonicalPath.replaceAll("\\.ser", ".metadata.ser"))
        if (mdFile.exists) {
          Some(Serializer.deserialize[DocumentMetadata](mdFile))
        } else None
      }
      (doc, md)
      // NOTE: we're assuming this is
    case gz if gz.endsWith("json.gz") =>
      val contents: String = GzipUtils.uncompress(f)
      val jast = parse(contents)
      val doc = JSONSerializer.toDocument(jast)
      (doc, None)
    case other =>
      throw new Exception(s"Cannot deserialize ${f.getName} to org.clulab.processors.Document. Unsupported extension '$other'")
  }

  def generateUUID: String = {
    java.util.UUID.randomUUID().toString
  }

  // generates a lucene document per sentence
  def mkDocumentBlock(d: ProcessorsDocument, metadata: Option[DocumentMetadata]): Seq[Document] = {
    // FIXME what should we do if the document has no id?
    val docId = d.id.getOrElse(generateUUID)

    val block = ArrayBuffer.empty[Document]
    for ((s, i) <- d.sentences.zipWithIndex) {
      if (s.size <= maxNumberOfTokensPerSentence) {
        block += mkSentenceDoc(s, docId, i.toString)
      } else {
        logger.warn(s"skipping sentence with ${s.size} tokens")
      }
    }
    block += mkParentDoc(docId, metadata)
    block
  }

  def mkParentDoc(docId: String, metadata: Option[DocumentMetadata]): Document = {
    val parent = new Document
    // FIXME these strings should probably be defined in the config, not hardcoded
    parent.add(new StringField("type", "parent", Store.NO))
    parent.add(new StringField("docId", docId, Store.YES))

    if (metadata.nonEmpty) {
      val md = metadata.get

      val authors: Seq[String]    = md.authors.map{ a => s"${a.givenName} ${a.surName}" }
      val     doi: Option[String] = md.doi.map(_.doi)
      val     url: Option[String] = if (md.doi.nonEmpty) {
        md.doi.get.url
      } else if (md.pmid.nonEmpty) {
        md.pmid.get.url
      } else {
        None
      }

      val pubYear: Option[Int]    = md.publicationDate match {
        case None => None
        case Some(pd) => pd.year
      }

      authors.foreach{ author: String => parent.add( new TextField("author", author, Store.YES)) }
      md.title.foreach(title => parent.add( new TextField("title", title, Store.YES)))
      // FIXME: should this be stored?
      md.journal.foreach(v => parent.add( new TextField("venue", v, Store.YES)))
      // FIXME: should this be stored?
      pubYear.foreach{ y =>
        parent.add( new IntPoint("year", y))
        parent.add( new StoredField("year", y))
      }
      // FIXME: should this be stored?
      doi.foreach(doi => parent.add( new TextField("doi", doi, Store.YES)))
      url.foreach { url =>
        parent.add( new TextField("url", url, Store.YES))
      }

    }

    parent
  }

  def mkSentenceDoc(s: Sentence, docId: String, sentId: String): Document = {
    val sent = new Document
    sent.add(new StoredField(documentIdField, docId))
    sent.add(new StoredField(sentenceIdField, sentId))
    sent.add(new NumericDocValuesField(sentenceLengthField, s.size.toLong))
    sent.add(new TextField(rawTokenField, new OdinsonTokenStream(s.raw)))
    // we want to index and store the words for displaying in the shell
    sent.add(new TextField(wordTokenField, s.words.mkString(" "), Store.YES))
    sent.add(new TextField(normalizedTokenField, new NormalizedTokenStream(s.raw, s.words)))
    if (s.tags.isDefined) {
      sent.add(new TextField(posTagTokenField, new OdinsonTokenStream(s.tags.get)))
    }
    if (s.lemmas.isDefined) {
      sent.add(new TextField(lemmaTokenField, new OdinsonTokenStream(s.lemmas.get)))
    }
    if (s.entities.isDefined) {
      sent.add(new TextField(entityTokenField, new OdinsonTokenStream(s.entities.get)))
    }
    if (s.chunks.isDefined) {
      sent.add(new TextField(chunkTokenField, new OdinsonTokenStream(s.chunks.get)))
    }
    if (s.dependencies.isDefined) {
      val deps = s.dependencies.get
      sent.add(new TextField(incomingTokenField, new DependencyTokenStream(deps.incomingEdges)))
      sent.add(new TextField(outgoingTokenField, new DependencyTokenStream(deps.outgoingEdges)))
      val graph = writer.mkDirectedGraph(deps.incomingEdges, deps.outgoingEdges, deps.roots.toArray)
      val bytes = graph.toBytes
      if (bytes.length <= sortedDocValuesFieldMaxSize) {
        sent.add(new SortedDocValuesField(dependenciesField, new BytesRef(bytes)))
      } else {
        logger.warn(s"serialized dependencies too big for storage: ${bytes.length} > $sortedDocValuesFieldMaxSize bytes")
      }
    }

    if (storeSentenceJson) {
      // store sentence JSON in index for use in webapp
      // NOTE: this will **greatly** increase the size of the index
      sent.add(new StoredField("json-binary", DocUtils.sentenceToBytes(s)))
    }

    sent
  }

}
