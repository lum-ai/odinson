package ai.lum.odinson.lucene


import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.document.{Document, Field, TextField}
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.Query
import org.apache.lucene.store.FSDirectory
import org.scalatest.{BeforeAndAfterEach, FlatSpecLike, Matchers}

import java.io.File
import scala.collection.JavaConverters._
import scala.reflect.io.Directory

class TestLuceneIndex extends FlatSpecLike with Matchers with BeforeAndAfterEach {

    private val indexDir : File = new File( "./target/lucene-indexer-test" )

    override def beforeEach( ) : Unit = {
        if ( indexDir.exists() ) new Directory( indexDir ).deleteRecursively()
    }

    override def afterEach( ) : Unit = {
        if ( indexDir.exists() ) new Directory( indexDir ).deleteRecursively()
    }

    "Incremental Lucene Index" should "be able to incrementally search documents as they are added" in {
        val index : LuceneIndex = new IncrementalLuceneIndex( directory = FSDirectory.open( indexDir.toPath ), computeTotalHits = true, refreshMs = 750 )

        // lucene field mappings
        val docIdField = "id"
        val fieldName = "text_content"

        // content
        val textOne = "michael is the best"
        val textTwo = "michael plays drums"

        val firstDoc : Document = {
            val doc = new Document()
            doc.add( new TextField( docIdField, "1", Field.Store.YES ) )
            doc.add( new TextField( fieldName, textOne, Field.Store.YES ) )
            doc
        }

        val secondDoc : Document = {
            val doc = new Document()
            doc.add( new TextField( docIdField, "2", Field.Store.YES ) )
            doc.add( new TextField( fieldName, textTwo, Field.Store.YES ) )
            doc
        }

        // write the first doc to the index and wait for the searcher refresh to run in the background
        index.write( Seq( firstDoc ).asJava )
        Thread.sleep( 1000 )
        val query : Query = new QueryParser( fieldName, new WhitespaceAnalyzer ).parse( "michael" )
        val firstDocResults = index.search( query )
        firstDocResults.totalHits shouldBe 1

        // now do the second doc and check
        index.write( Seq( secondDoc ).asJava )
        Thread.sleep( 1000 )
        val incrementalResults = index.search( query ) // reuse the same query

        incrementalResults.totalHits shouldBe 2
    }


}
