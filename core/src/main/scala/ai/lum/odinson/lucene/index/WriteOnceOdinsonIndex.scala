//package ai.lum.odinson.lucene.index
//
//import ai.lum.odinson.lucene.OdinResults
//import ai.lum.odinson.lucene.search.{OdinsonIndexSearcher, OdinsonQuery, OdinsonScoreDoc}
//import ai.lum.odinson.utils.IndexSettings
//import ai.lum.odinson.utils.exceptions.OdinsonException
//import ai.lum.odinson.{LazyIdGetter, Document => OdinsonDocument}
//import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
//import org.apache.lucene.analysis.{Analyzer, TokenStream}
//import org.apache.lucene.document.{Document => LuceneDocument}
//import org.apache.lucene.index.IndexWriterConfig.OpenMode
//import org.apache.lucene.index.{DirectoryReader, Fields, IndexReader, IndexWriter, IndexWriterConfig}
//import org.apache.lucene.search.highlight.TokenSources
//import org.apache.lucene.search.{Collector, CollectorManager, IndexSearcher, Query, TopDocs}
//import org.apache.lucene.store.Directory
//import org.slf4j.{Logger, LoggerFactory}
//
//import scala.collection.JavaConverters._
//import scala.collection.mutable.ArrayBuffer
//import scala.concurrent.ExecutionContext
//
//class WriteOnceOdinsonIndex( override val directory : Directory,
//                             override val settings : IndexSettings,
//                             override val computeTotalHits : Boolean,
//                             override val displayField : String,
//                             override val normalizedTokenField : String,
//                             override val addToNormalizedField : Set[ String ],
//                             override val incomingTokenField : String,
//                             override val outgoingTokenField : String,
//                             override val maxNumberOfTokensPerSentence : Int,
//                             override val invalidCharacterReplacement : String,
//                             protected val refreshMs : Long = -1 ) extends OdinsonIndex {
//
//    private val LOG : Logger = LoggerFactory.getLogger( getClass )
//
//    private implicit val ec : ExecutionContext = ExecutionContext.global
//
//    private val odinsonWriter : OdinsonIndexWriter = {
//        val config = new IndexWriterConfig( this.analyzer )
//        config.setOpenMode( OpenMode.CREATE_OR_APPEND )
//        val writer = new IndexWriter( directory, config )
//        new OdinsonIndexWriter( writer,
//                                directory,
//                                vocabulary,
//                                settings,
//                                normalizedTokenField,
//                                addToNormalizedField,
//                                incomingTokenField,
//                                outgoingTokenField,
//                                maxNumberOfTokensPerSentence,
//                                invalidCharacterReplacement,
//                                displayField )
//    }
//
//    override def indexOdinsonDoc( doc : OdinsonDocument ) : Unit = {
//        val docs = odinsonWriter.mkDocumentBlock( doc )
//        write( docs.asJava )
//    }
//
//    override def lazyIdGetter( luceneDocId : Int ) : LazyIdGetter = {
//        new LazyIdGetter( this, luceneDocId )
//    }
//
//    override def search( query : Query, limit : Int ) : TopDocs = {
//        try {
//            val searcher : IndexSearcher = new OdinsonIndexSearcher( reader, computeTotalHits )
//            searcher.search( query, limit )
//        }
//        catch {
//            case e : Throwable => throw new RuntimeException( "what is the best way to deal with this?" )
//        }
//
//    }
//
//    override def search[ CollectorType <: Collector, ResultType ]( query : Query, manager : CollectorManager[ CollectorType, ResultType ] ) : ResultType = {
//        try {
//            val searcher : IndexSearcher = new OdinsonIndexSearcher( getIndexReader(), computeTotalHits )
//            searcher.search[ CollectorType, ResultType ]( query, manager )
//        } catch {
//            case e : Throwable => {
//                e.printStackTrace()
//                throw new RuntimeException( "what is the best way to deal with this?" )
//            }
//        }
//    }
//
//    override def write( block : java.util.Collection[ LuceneDocument ] ) : Unit = {
//        odinsonWriter.addDocuments( block )
//    }
//
//    override def doc( docId : Int ) : LuceneDocument = {
//        try {
//            val reader : IndexReader = DirectoryReader.open( directory )
//            reader.document( docId )
//        }
//        catch {
//            case e : Throwable => throw new RuntimeException( "what is the best way to deal with this?" )
//        }
//
//    }
//
//    def doc( docId : Int, fieldNames : Set[ String ] ) : LuceneDocument = {
//        try {
//            val reader : IndexReader = DirectoryReader.open( directory )
//            reader.document( docId, fieldNames.asJava )
//        }
//        catch {
//            case e : Throwable => {
//                e.printStackTrace()
//                throw new RuntimeException( "what is the best way to deal with this?", e )
//            }
//        }
//
//    }
//
//    override def getTermVectors( docId : Int ) : Fields = {
//        try {
//            val reader : IndexReader = DirectoryReader.open( directory )
//            reader.getTermVectors( docId )
//        }
//        catch {
//            case e : Throwable => throw new RuntimeException( "what is the best way to deal with this?" )
//        }
//
//    }
//
//    override def getTokens( doc : LuceneDocument,
//                            termVectors : Fields,
//                            fieldName : String ) : Array[ String ] = {
//
//        val field = doc.getField( fieldName )
//        if ( field == null ) throw new OdinsonException( s"Attempted to getTokens from field that was not stored: $fieldName" )
//        val text = field.stringValue
//        val ts = TokenSources.getTokenStream( fieldName, termVectors, text, analyzer, -1 )
//        val tokens = getTokens( ts )
//        tokens
//    }
//
//    private def getTokens( ts : TokenStream ) : Array[ String ] = {
//        ts.reset()
//        val terms = new ArrayBuffer[ String ]
//
//        while ( ts.incrementToken() ) {
//            val charTermAttribute = ts.addAttribute( classOf[ CharTermAttribute ] )
//            val term = charTermAttribute.toString
//            terms += term
//        }
//
//        ts.end()
//        ts.close()
//
//        terms.toArray
//    }
//
//    override def getTokensFromMultipleFields( docID : Int, fieldNames : Set[ String ] ) : Map[ String, Array[ String ] ] = {
//        val luceneDoc = doc( docID, fieldNames )
//        val tvs = getTermVectors( docID )
//        fieldNames
//          .map( field => (field, getTokens( luceneDoc, tvs, field, analyzer )) )
//          .toMap
//    }
//
//    override def getTokens( doc : LuceneDocument,
//                            tvs : Fields,
//                            fieldName : String,
//                            analyzer : Analyzer ) : Array[ String ] = {
//        val field = doc.getField( fieldName )
//        if ( field == null ) throw new OdinsonException(
//            s"Attempted to getTokens from field that was not stored: $fieldName"
//            )
//        val text = field.stringValue
//        val ts = TokenSources.getTokenStream( fieldName, tvs, text, analyzer, -1 )
//        val tokens = getTokens( ts )
//        tokens
//    }
//
//    override def refresh( ) : Unit = {
//        odinsonWriter.flush()
//        odinsonWriter.commit()
//    }
//
//    override def numDocs( ) : Int = {
//        try {
//            val reader : IndexReader = DirectoryReader.open( directory )
//            reader.numDocs()
//        }
//        catch {
//            case e : Throwable => {
//                e.printStackTrace()
//                throw new RuntimeException( "what is the best way to deal with this?" )
//            }
//        }
//
//    }
//
//    override def maxDoc( ) : Int = {
//        try {
//            val reader : IndexReader = DirectoryReader.open( directory )
//            reader.maxDoc()
//        }
//        catch {
//            case e : Throwable => throw new RuntimeException( "what is the best way to deal with this?" )
//        }
//
//    }
//
//    override def listFields( ) : Fields = {
//        ???
//    }
//
//    override def close( ) : Unit = {
//        dumpSettings()
//        odinsonWriter.close()
//        directory.close()
//    }
//
//    override def search( scoreDoc : OdinsonScoreDoc, query : OdinsonQuery, cappedHits : Int, disableMatchSelector : Boolean ) : OdinResults = {
//        val manager = new OdinsonCollectorManager( scoreDoc, cappedHits, computeTotalHits, disableMatchSelector )
//        this.search( query, manager )
//    }
//
//    override def getIndexReader( ) : IndexReader = DirectoryReader.open( directory )
//
//}
