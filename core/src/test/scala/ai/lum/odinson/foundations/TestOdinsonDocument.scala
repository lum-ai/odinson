package ai.lum.odinson.foundations

import java.util.Date
import java.text.SimpleDateFormat

import org.scalatest._
import ai.lum.odinson.{Document, BaseSpec}

import ai.lum.odinson.{TokensField, GraphField, Sentence, Field, StringField, DateField}

class TestOdinsonDocument extends BaseSpec {
  // Testing
  "OdinsonDocument TokensField" should "handle a json String correctly" in {
    val field =
      """{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]}"""
    val fieldPretty =
      """|{
        |    "$type": "ai.lum.odinson.TokensField",
        |    "name": "chunk",
        |    "tokens": [
        |        "B-NP",
        |        "B-VP",
        |        "B-NP",
        |        "I-NP",
        |        "O"
        |    ]
        |}""".stripMargin
    //
    val tokenField = TokensField.fromJson(field)
    // check if the name is being parsed correct
    tokenField.name should be("chunk")
    // check if default is there
    tokenField.store should be(false)
    // test toJson
    tokenField.toJson should equal(field)
    // test pretty
    tokenField.toPrettyJson should equal(fieldPretty)
    // first
    tokenField.tokens.head should equal("B-NP")
    // last
    tokenField.tokens.last should equal("O")
  }

  "OdinsonDocument GraphField" should "handle a json String correctly" in {
    val field =
      """{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[3,2,"amod"]],"roots":[1]}"""
    // parse json
    val graphField = GraphField.fromJson(field)
    // test name
    graphField.name shouldBe ("dependencies")
    // test roots
    graphField.roots shouldBe a[Set[_]]
    graphField.roots.head should equal(1)
    // test store
    graphField.store should be(false)
    // test firs and last elements
    graphField.edges.head shouldBe (1, 0, "nsubj")
    graphField.edges.last shouldBe (3, 2, "amod")
  }

  // case class Sentence
  "OdinsonDocument Sentence" should "handle a json String correctly" in {
    val sentence =
      """{"numTokens":1,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"]],"roots":[1]}]}"""
    // parse json
    val sentenceObj = Sentence.fromJson(sentence)
    // check the namber of tokens
    sentenceObj.numTokens shouldEqual (1)
    // make sure it gets the type of each field right
    sentenceObj.fields.head shouldBe a[Field]
    sentenceObj.fields.last shouldBe a[Field]
    // check the type of both
    val lastFieldType = sentenceObj.fields.last match {
      case t: GraphField => "GraphField"
      case _             => "other"
    }
    lastFieldType shouldBe ("GraphField")
    // maybe test internals? ask
  }

  "OdinsonDocument StringField" should "handle a json String correctly" in {
    val field =
      """{"$type":"ai.lum.odinson.StringField","name":"smth","string":"smthString"}"""
    // parse field
    val stringField = StringField.fromJson(field)
    // check stuff
    stringField.store shouldBe (false)
    stringField.name shouldBe ("smth")
    stringField.string shouldBe ("smthString")
  }

  "OdinsonDocument DateField" should "handle json String and local date correctly" in {
    val field =
      """{"$type":"ai.lum.odinson.DateField","name":"smth","date":"1993-03-28"}"""
    // parse
    var dateField = DateField.fromJson(field)
    // test values
    dateField.date shouldBe ("1993-03-28")
    dateField.name shouldBe ("smth")
    // test the parsed date
    dateField.localDate.getYear shouldBe (1993)
    dateField.localDate.getDayOfMonth shouldBe (28)
    dateField.localDate.getMonthValue shouldBe (3)
    
    val localDate = dateField.localDate
    dateField = DateField.fromLocalDate("smth", localDate, false)
    dateField.date shouldBe ("1993-03-28")
    dateField.name shouldBe ("smth")

  }
  
  "OdinsonDocument DateField" should "handle java date correctly" in {
    // testing java date
    val formatter = new SimpleDateFormat("dd/MM/yyyy")
    val javaDate = formatter.parse("28/03/1993")
    val dateField = DateField.fromDate("smth", javaDate, false)
    dateField.date shouldBe ("1993-03-28")
    dateField.localDate.getYear shouldBe (1993)
    dateField.localDate.getDayOfMonth shouldBe (28)
    dateField.localDate.getMonthValue shouldBe (3)
  
  }
  // TODO: LocalDate parsing
  //
  //
}
