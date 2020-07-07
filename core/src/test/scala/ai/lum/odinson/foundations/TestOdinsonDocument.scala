package ai.lum.odinson.foundations

import org.scalatest._
import ai.lum.odinson.{Document, BaseSpec}

import ai.lum.odinson.{TokensField, GraphField}

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
    tokenField.toJson should equal (field)
    // test pretty
    tokenField.toPrettyJson should equal(fieldPretty)
    // first
    tokenField.tokens(0) should equal ("B-NP")
    // last
    tokenField.tokens(4) should equal ("O")
  }
  
  "OdinsonDocument GraphField" should "handle a json String correctly" in {
    val field = """{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}"""
    // parse json
    val graphField = GraphField.fromJson(field)
    // test name
    graphField.name shouldBe ("dependencies")
    // test roots
    graphField.roots shouldBe a [Set[_]]
    graphField.roots.head should equal (1)
    // test store
    graphField.store should be (false)
    // test firs and last elements
    graphField.edges.head shouldBe (1, 0, "nsubj")
    graphField.edges(3) shouldBe (3, 2, "amod")
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
