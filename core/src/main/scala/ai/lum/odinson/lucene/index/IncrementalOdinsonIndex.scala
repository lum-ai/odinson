package ai.lum.odinson.lucene.index

import ai.lum.odinson.lucene.OdinResults
import ai.lum.odinson.lucene.search.{OdinsonQuery, OdinsonScoreDoc}
import ai.lum.odinson.utils.IndexSettings
import ai.lum.odinson.utils.exceptions.OdinsonException
import ai.lum.odinson.{LazyIdGetter, Document => OdinsonDocument}
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.analysis.{Analyzer, TokenStream}
import org.apache.lucene.document.{Document => LuceneDocument}
import org.apache.lucene.index.IndexWriterConfig.OpenMode
import org.apache.lucene.index.{DirectoryReader, Fields, IndexReader, IndexWriter, IndexWriterConfig}
import org.apache.lucene.search.highlight.TokenSources
import org.apache.lucene.search.{Collector, CollectorManager, IndexSearcher, Query, SearcherManager, TopDocs}
import org.apache.lucene.store.Directory
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class IncrementalOdinsonIndex( override val directory : Directory,
                               override val settings : IndexSettings,
                               override val computeTotalHits : Boolean,
                               override val displayField : String,
                               override val normalizedTokenField : String,
                               override val addToNormalizedField : Set[ String ],
                               override val incomingTokenField : String,
                               override val outgoingTokenField : String,
                               override val maxNumberOfTokensPerSentence : Int,
                               override val invalidCharacterReplacement : String,
                               protected val refreshMs : Int = -1 ) extends OdinsonIndex {

    private val LOG : Logger = LoggerFactory.getLogger( getClass )

    private implicit val ec : ExecutionContext = ExecutionContext.global

    private val odinsonWriter : OdinsonIndexWriter = {
        val config = new IndexWriterConfig( this.analyzer )
        config.setOpenMode( OpenMode.CREATE_OR_APPEND )
        val writer = new IndexWriter( directory, config )

        new OdinsonIndexWriter( writer,
                                directory,
                                vocabulary,
                                settings,
                                normalizedTokenField,
                                addToNormalizedField,
                                incomingTokenField,
                                outgoingTokenField,
                                maxNumberOfTokensPerSentence,
                                invalidCharacterReplacement,
                                displayField )

    }

    // access to a singleton lucene reader that was not acquired from search manager
    private val luceneReader : IndexReader = DirectoryReader.open( odinsonWriter.writer )

    private val manager : SearcherManager = new SearcherManager( odinsonWriter.writer, new OdinsonSearcherFactory( computeTotalHits ) )

    if ( refreshMs > 1 ) refreshPeriodically()

    override def indexOdinsonDoc( doc : OdinsonDocument ) : Unit = {
        write( odinsonWriter.mkDocumentBlock( doc ).asJava )
    }

    override def lazyIdGetter( luceneDocId : Int ) : LazyIdGetter = {
        new LazyIdGetter( this, luceneDocId )
    }

    override def search( query : Query, limit : Int ) : TopDocs = {
        var searcher : IndexSearcher = null
        try {
            searcher = acquireSearcher()
            searcher.search( query, limit )
        } catch {
            case e : Throwable => throw new RuntimeException( "what is the best way to deal with this?" )
        }
        finally releaseSearcher( searcher )
    }

    override def search[ CollectorType <: Collector, ResultType ]( query : Query, manager : CollectorManager[ CollectorType, ResultType ] ) : ResultType = {
        var searcher : IndexSearcher = null
        try {
            searcher = acquireSearcher()
            searcher.search[ CollectorType, ResultType ]( query, manager )
        } catch {
            case e : Throwable => throw new RuntimeException( "what is the best way to deal with this?" )
        }
        finally releaseSearcher( searcher )
    }

    override def write( block : java.util.Collection[ LuceneDocument ] ) : Unit = {
        odinsonWriter.addDocuments( block )
        refresh()
    }

    override def doc( docId : Int ) : LuceneDocument = {
        var searcher : IndexSearcher = null
        try {
            searcher = acquireSearcher()
            searcher.getIndexReader.document( docId )
        } catch {
            case e : Throwable => throw new RuntimeException( "what is the best way to deal with this?" )
        }
        finally releaseSearcher( searcher )
    }

    def doc( docId : Int, fieldNames : Set[ String ] ) : LuceneDocument = {
        var searcher : IndexSearcher = null
        try {
            searcher = acquireSearcher()
            searcher.getIndexReader.document( docId, fieldNames.asJava )
        } catch {
            case e : Throwable => throw new RuntimeException( "what is the best way to deal with this?" )
        }
        finally releaseSearcher( searcher )
    }

    override def getTermVectors( docId : Int ) : Fields = {
        var searcher : IndexSearcher = null
        try {
            searcher = acquireSearcher()
            searcher.getIndexReader.getTermVectors( docId )
        } catch {
            case e : Throwable => throw new RuntimeException( "what is the best way to deal with this?" )
        }
        finally releaseSearcher( searcher )
    }

    override def getTokens( doc : LuceneDocument,
                            termVectors : Fields,
                            fieldName : String ) : Array[ String ] = {

        val field = doc.getField( fieldName )
        if ( field == null ) throw new OdinsonException( s"Attempted to getTokens from field that was not stored: $fieldName" )
        val text = field.stringValue
        val ts = TokenSources.getTokenStream( fieldName, termVectors, text, analyzer, -1 )
        val tokens = getTokens( ts )
        tokens
    }

    private def getTokens( ts : TokenStream ) : Array[ String ] = {
        ts.reset()
        val terms = new ArrayBuffer[ String ]

        while ( ts.incrementToken() ) {
            val charTermAttribute = ts.addAttribute( classOf[ CharTermAttribute ] )
            val term = charTermAttribute.toString
            terms += term
        }

        ts.end()
        ts.close()

        terms.toArray
    }

    override def getTokensFromMultipleFields( docID : Int, fieldNames : Set[ String ] ) : Map[ String, Array[ String ] ] = {
        val luceneDoc = doc( docID, fieldNames )
        val tvs = getTermVectors( docID )
        fieldNames
          .map( field => (field, getTokens( luceneDoc, tvs, field, analyzer )) )
          .toMap
    }

    override def getTokens( doc : LuceneDocument,
                            tvs : Fields,
                            fieldName : String,
                            analyzer : Analyzer ) : Array[ String ] = {
        val field = doc.getField( fieldName )
        if ( field == null ) throw new OdinsonException(
            s"Attempted to getTokens from field that was not stored: $fieldName"
            )
        val text = field.stringValue
        val ts = TokenSources.getTokenStream( fieldName, tvs, text, analyzer, -1 )
        val tokens = getTokens( ts )
        tokens
    }

    override def refresh( ) : Unit = {
        odinsonWriter.flush()
        manager.maybeRefresh()
    }

    override def numDocs( ) : Int = {
        var searcher : IndexSearcher = null
        try {
            searcher = acquireSearcher()
            searcher.getIndexReader.numDocs()
        } catch {
            case e : Throwable => throw new RuntimeException( "what is the best way to deal with this?" )
        }
        finally releaseSearcher( searcher )
    }

    override def maxDoc( ) : Int = {
        var searcher : IndexSearcher = null
        try {
            searcher = acquireSearcher()
            searcher.getIndexReader.maxDoc()
        } catch {
            case e : Throwable => throw new RuntimeException( "what is the best way to deal with this?" )
        }
        finally releaseSearcher( searcher )
    }

    override def listFields( ) : Fields = {
        ??? //TODO: what operation uses this call
    }

    private def acquireSearcher( ) : IndexSearcher = manager.acquire()

    private def releaseSearcher( searcher : IndexSearcher ) : Unit = manager.release( searcher )

    private def refreshPeriodically( ) : Unit = {
        Future {
            println( "refreshing index searchers with updated data" )
            Thread.sleep( refreshMs )
            refresh()
        } onComplete {
            case Success( _ ) => refreshPeriodically()
            case Failure( e : Throwable ) => e.printStackTrace()
        }
    }

    override def close( ) : Unit = {
        dumpSettings()
        odinsonWriter.flush()
        odinsonWriter.commit()
        odinsonWriter.close()
        luceneReader.close()
    }

    override def search( scoreDoc : OdinsonScoreDoc, query : OdinsonQuery, cappedHits : Int, disableMatchSelector : Boolean ) : OdinResults = {
        val manager = new OdinsonCollectorManager( scoreDoc, cappedHits, computeTotalHits, disableMatchSelector )
        this.search( query, manager )
    }

    override def getIndexReader( ) : IndexReader = luceneReader

}
