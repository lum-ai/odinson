package ai.lum.odinson.foundations

import org.scalatest._
import ai.lum.odinson.{Document, BaseSpec}

import ai.lum.odinson.{TokensField}

class TestOdinsonDocument extends BaseSpec {
  // TODO: case class Sentence
  // TODO: test case class field
  // TODO: test tokensField read
  "OdinsonDocument" should "handle a json String correctly" in {
    val field =
      """{
        "$type": "ai.lum.odinson.TokensField",
        "name": "chunk",
        "tokens": [
          "B-NP",
          "B-VP",
          "B-NP",
          "I-NP",
          "O"
        ]
      }"""
    //
    val tokenField = TokensField.fromJson(field)
    // check if the name is being parsed correct
    tokenField.name shouldBe "chunk"
    // check if default is there
    tokenField.store shouldBe false
    // TODO: test individual tokens
    // first
    // last
  }
  // TODO: test graphfield read
  // TODO: test stringfield read
  // TODO: datefield read
  // TODO: LocalDate parsing
  // TODO: document toJson
  // TODO: document toPrettyJson
  //
  //
}
