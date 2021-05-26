package ai.lum.odinson.lucene

import ai.lum.odinson.lucene.search.OdinsonIndexSearcher
import org.apache.lucene.index.IndexReader
import org.apache.lucene.search.{IndexSearcher, SearcherFactory}

class OdinsonSearcherFactory( computeTotalHits : Boolean ) extends SearcherFactory {

    override def newSearcher( reader : IndexReader, previousReader : IndexReader ) : IndexSearcher = {
        new OdinsonIndexSearcher( reader, computeTotalHits )
    }

}
