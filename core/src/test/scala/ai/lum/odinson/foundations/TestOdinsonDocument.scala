package ai.lum.odinson.foundations

import java.text.SimpleDateFormat
import java.io.File

import scala.io.Source
import ai.lum.odinson.Document
import ai.lum.odinson.utils.TestUtils.OdinsonTest
import ai.lum.odinson.{DateField, Field, GraphField, Sentence, StringField, TokensField}

class TestOdinsonDocument extends OdinsonTest {
  "OdinsonDocument Document" should "handle a json File correctly" in {
    // Check my code to see how to open a file like this
    val jsonFile =
      new File(getClass.getResource("/odinsonDocTest.json").getPath)
    // Open the one life file
    val docObj = Document.fromJson(jsonFile)
    // check if toJson is working
    docObj.toJson shouldEqual (Source
      .fromResource("odinsonDocTest.json")
      .getLines
      .mkString)
    // check if toPrettyJson is working
    docObj.toPrettyJson shouldEqual (Source
      .fromResource("odinsonDocTestPretty.json")
      .getLines
      .mkString("\n"))
    // check if the fields are being loaded correctly
    docObj.id shouldBe ("foo")
    docObj.sentences.head.numTokens should be(1)
    docObj.sentences.head.fields.head.name should be("raw")
  }

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
        |}""".stripMargin.replace("\r", "")
    //
    val tokenField = TokensField.fromJson(field)
    // check if the name is being parsed correct
    tokenField.name should be("chunk")
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
    sentenceObj.toJson shouldEqual (sentence)
    //
    val prettySentence = """|{
    |    "numTokens": 1,
    |    "fields": [
    |        {
    |            "$type": "ai.lum.odinson.TokensField",
    |            "name": "raw",
    |            "tokens": [
    |                "Becky"
    |            ]
    |        },
    |        {
    |            "$type": "ai.lum.odinson.GraphField",
    |            "name": "dependencies",
    |            "edges": [
    |                [
    |                    1,
    |                    0,
    |                    "nsubj"
    |                ]
    |            ],
    |            "roots": [
    |                1
    |            ]
    |        }
    |    ]
    |}""".stripMargin.replace("\r", "")
    // test pretty
    sentenceObj.toPrettyJson shouldEqual (prettySentence)
  }

  "OdinsonDocument StringField" should "handle a json String correctly" in {
    val field =
      """{"$type":"ai.lum.odinson.StringField","name":"smth","string":"smthString"}"""
    // parse field
    val stringField = StringField.fromJson(field)
    // check stuff
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
}
