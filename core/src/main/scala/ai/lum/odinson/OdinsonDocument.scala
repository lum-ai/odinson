package ai.lum.odinson

import java.io.File
import java.util.Date
import java.time.{ LocalDate, ZoneId }
import scala.collection.mutable.ArrayBuilder
import upickle.default._
import ai.lum.common.FileUtils._
import ai.lum.odinson.lucene.index.OdinsonIndexWriter
import ai.lum.odinson.utils.exceptions.OdinsonException
import com.typesafe.scalalogging.LazyLogging

case class Document(
  id: String,
  metadata: Seq[Field],
  sentences: Seq[Sentence]
) extends LazyLogging {
  def toJson: String = write(this)
  def toPrettyJson: String = write(this, indent = 4)

  def addMetadata(metadataIn: Seq[Field], append: Boolean): Document = {
    // Warn about not-queryable StringFields
    metadataIn
      .collect {
        case sf: StringField if !OdinsonIndexWriter.STRING_FIELD_EXCEPTIONS.contains(sf.name) => sf
      }
      .foreach { sf => logger.info(OdinsonIndexWriter.STRING_FIELD_WARNING(sf.name)) }

    if (append) {
      this.copy(metadata = metadata ++ metadataIn)
    } else {
      this.copy(metadata = metadataIn)
    }
  }

}

object Document {

  implicit val rw: ReadWriter[Document] = macroRW

  def fromJson(data: String): Document = {
    read[Document](data)
  }

  def fromJson(f: File): Document = {
    fromJson(f.readString())
  }

}

case class Sentence(
  numTokens: Int,
  fields: Seq[Field]
) {
  def toJson: String = write(this)
  def toPrettyJson: String = write(this, indent = 4)
}

object Sentence {
  implicit val rw: ReadWriter[Sentence] = macroRW

  def fromJson(data: String): Sentence = {
    read[Sentence](data)
  }

}

sealed trait Field {
  def name: String
  def toJson: String = write(this)
  def toPrettyJson: String = write(this, indent = 4)
}

object Field {

  implicit val rw: ReadWriter[Field] = {
    ReadWriter.merge(
      TokensField.rw,
      GraphField.rw,
      StringField.rw,
      DateField.rw,
      NumberField.rw,
      NestedField.rw
    )
  }

}

case class TokensField(
  name: String,
  tokens: Seq[String]
) extends Field

object TokensField {
  implicit val rw: ReadWriter[TokensField] = macroRW

  def fromJson(data: String): TokensField = {
    read[TokensField](data)
  }

}

case class GraphField(
  name: String,
  edges: Seq[(Int, Int, String)],
  roots: Set[Int]
) extends Field {

  def mkIncomingEdges(numTokens: Int): Array[Array[(Int, String)]] = {
    val incoming = Array.fill(numTokens)(new ArrayBuilder.ofRef[(Int, String)])
    for ((src, dst, label) <- edges) {
      incoming(dst) += Tuple2(src, label)
    }
    incoming.map(_.result())
  }

  def mkOutgoingEdges(numTokens: Int): Array[Array[(Int, String)]] = {
    val outgoing = Array.fill(numTokens)(new ArrayBuilder.ofRef[(Int, String)])
    for ((src, dst, label) <- edges) {
      outgoing(src) += Tuple2(dst, label)
    }
    outgoing.map(_.result())
  }

}

object GraphField {
  implicit val rw: ReadWriter[GraphField] = macroRW

  def fromJson(data: String): GraphField = {
    read[GraphField](data)
  }

}

case class StringField(
  name: String,
  string: String
) extends Field

object StringField {
  implicit val rw: ReadWriter[StringField] = macroRW

  def fromJson(data: String): StringField = {
    read[StringField](data)
  }

}

case class DateField(
  name: String,
  date: String
) extends Field {
  val localDate = LocalDate.parse(date)
}

object DateField {

  implicit val rw: ReadWriter[DateField] = macroRW

  def fromJson(data: String): DateField = {
    read[DateField](data)
  }

  def fromDate(name: String, date: Date, store: Boolean = false): DateField = {
    val localDate = date.toInstant.atZone(ZoneId.of("UTC")).toLocalDate
    fromLocalDate(name, localDate)
  }

  def fromLocalDate(name: String, date: LocalDate, store: Boolean = false): DateField = {
    DateField(name, date.toString)
  }

}

case class NumberField(name: String, value: Double) extends Field

object NumberField {

  implicit val rw: ReadWriter[NumberField] = macroRW

  def fromJson(data: String): NumberField = {
    read[NumberField](data)
  }

}

case class NestedField(name: String, fields: Seq[Field]) extends Field

object NestedField {

  implicit val rw: ReadWriter[NestedField] = macroRW

  def fromJson(data: String): NestedField = {
    read[NestedField](data)
  }

}

/** Helper class for reading and writing metadata companion files.
  * Specifically, if you want to make a json file to store document metadata,
  * you can create an instance of this class, ensuring that the docId matches
  * that of the corresponding document.  Then you can serialize/deserialize
  * easily.  Also, note that this class plays well with the app that adds the
  * metadata to the document: ai.lum.odinson.extra.AddMetadataToDocuments
  */
case class MetadataWrapper(docId: String, fields: Seq[Field]) {
  def toJson: String = write(this)
}

object MetadataWrapper {
  implicit val rw: ReadWriter[MetadataWrapper] = macroRW

  def fromJson(data: String): MetadataWrapper = {
    read[MetadataWrapper](data)
  }

}
