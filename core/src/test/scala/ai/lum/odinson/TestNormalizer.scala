package ai.lum.odinson

import org.scalatest._
import ai.lum.odinson.utils.Normalizer

class TestNormalizer extends FlatSpec with Matchers {

  "Normalizer" should "normalize unicode strings" in {
    val c1 = "caf\u00e9"
    val c2 = "cafe\u0301"
    c1 should not equal c2
    Normalizer.normalize(c1) shouldEqual Normalizer.normalize(c2)
    Normalizer.normalize(c1, true) shouldEqual Normalizer.normalize(c2, true)
  }

  it should "support aggressive normalization" in {
    val c1 = "\u00bd"
    val c2 = "1/2"
    c1 should not equal c2
    Normalizer.normalize(c1) should not equal Normalizer.normalize(c2)
    Normalizer.normalize(c1, true) shouldEqual Normalizer.normalize(c2, true)
  }

  it should "support casefolding" in {
    val c1 = "\u00df"
    val c2 = "ss"
    c1 should not equal c2
    Normalizer.normalize(c1) should not equal Normalizer.normalize(c2)
    Normalizer.normalize(c1, true) shouldEqual Normalizer.normalize(c2, true)
  }

  it should "remove diacritics" in {
    val c1 = "caf\u00e9"
    val c2 = "cafe"
    c1 should not equal c2
    Normalizer.normalize(c1) should not equal Normalizer.normalize(c2)
    Normalizer.normalize(c1, true) shouldEqual Normalizer.normalize(c2, true)
  }

  it should "replace characters" in {
    // example from "Fluent Python"
    val text1 = "“Herr Voß: • ½ cup of Œtker™ caffè latte • bowl of açaí.”"
    val text2 = "\"Herr Voss: - 1/2 cup of OEtker(TM) caffe latte - bowl of acai.\""
    println(Normalizer.normalize(text1, true))
    text1 should not equal text2
    Normalizer.normalize(text1) should not equal Normalizer.normalize(text2)
    Normalizer.normalize(text1, true) shouldEqual Normalizer.normalize(text2, true)
  }

}
