package ai.lum.odinson.lucene

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.index.IndexWriterConfig.OpenMode
import org.apache.lucene.index.{IndexWriter, IndexWriterConfig}
import org.apache.lucene.search.{IndexSearcher, Query, SearcherManager, TopDocs}
import org.apache.lucene.store.Directory

import java.util.Collection
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


trait LuceneIndex {

    protected val analyzer : Analyzer = new WhitespaceAnalyzer

    protected val directory : Directory

    protected val computeTotalHits : Boolean

    def write( block : Collection[ Document ] ) : Unit

    def search( query : Query, limit : Int = 1000000000 ) : TopDocs

    def numDocs( ) : Int

    def refresh( ) : Unit

}

class IncrementalLuceneIndex( val directory : Directory, val computeTotalHits : Boolean, refreshMs : Long = 1000 ) extends LuceneIndex {

    private implicit val ec : ExecutionContext = ExecutionContext.global

    private val writer : IndexWriter = {
        val config = new IndexWriterConfig( this.analyzer )
        config.setOpenMode( OpenMode.CREATE_OR_APPEND )
        new IndexWriter( this.directory, config )
    }

    private val manager : SearcherManager = new SearcherManager( writer, new OdinsonSearcherFactory( computeTotalHits ) )

    refreshPeriodically()

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

    override def write( block : Collection[ Document ] ) : Unit = {
        println( s"adding document ${block.asScala.foreach( println )}" )
        writer.addDocuments( block )
        refresh()
    }

    override def refresh( ) : Unit = {
        writer.flush()
        writer.commit()
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

    private def acquireSearcher( ) : IndexSearcher = manager.acquire()

    private def releaseSearcher( searcher : IndexSearcher ) : Unit = manager.release( searcher )

    private def refreshPeriodically( ) : Unit = {
        Future {
            println( "refreshing index searchers with updated data" )
            Thread.sleep( refreshMs )
            refresh()
        } onComplete {
            case Success( _ ) => refreshPeriodically()
            case Failure( e : Throwable ) => ???
        }
    }

}
