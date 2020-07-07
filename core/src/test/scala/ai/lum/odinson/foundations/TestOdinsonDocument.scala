package ai.lum.odinson.foundations

import org.scalatest._
import ai.lum.odinson.{Document, BaseSpec}

import ai.lum.odinson.{TokensField}

class TestOdinsonDocument extends BaseSpec {
  "OdinsonDocument" should "handle a json String correctly" in {
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
    tokenField.tokens(0) should equal ("B-NP")
    // last
    tokenField.tokens(4) should equal ("O")
  }
  

  // TODO: case class Sentence
  // TODO: test case class field
  // TODO: test tokensField read
  // TODO: test graphfield read
  // TODO: test stringfield read
  // TODO: datefield read
  // TODO: LocalDate parsing
  // TODO: document toJson
  // TODO: document toPrettyJson
  //
  //
}
