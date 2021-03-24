package ai.lum.odinson.foundations

import ai.lum.odinson.utils.TestUtils.OdinsonTest

class TestMatchSelector extends OdinsonTest  {

    val t1 = Seq("The", "first", "project", ",", "amounting", "to", "about", "330", "million", "dollars", "will", "finance", "several", "small", "to", "medium", "hydropower", "plants", "in", "the", "Northwest", "Frontier", "Province", "and", "Punjab", ",", "the", "ADB", "said", "in", "a", "statement", "from", "its", "headquarters", "in", "Manila", ".")
    val t2 = Seq("KARMANOS", "-LRB-", "undated", "-RRB-", "-", "Nearly", "three", "years", "after", "separating", "from", "the", "Detroit", "Medical", "Center", "to", "form", "its", "own", "freestanding", "hospital", ",", "the", "Barbara", "Ann", "Karmanos", "Cancer", "Institute", "in", "Detroit", "is", "still", "struggling", "to", "expand", "beyond", "its", "space", "on", "the", "DMC", "'s", "Midtown", "campus", ".")
    val t3 = Seq("NameMedia", ",", "which", "began", "in", "1999", "as", "YesDirect", ",", "was", "reintroduced", "in", "May", "2006", "with", "an", "undisclosed", "amount", "of", "equity", "financing", "from", "Highland", "Capital", "Partners", "and", "Summit", "Partners", ",", "two", "Boston", "venture", "capital", "groups", ",", "and", "more", "than", "$", "100", "million", "in", "debt", "financing", "from", "Goldman", "Sachs", ".")

  val x ="[tag=NN | tag=NNS] [word=said]? [word=in]? [word=two]? [word=the]? [word=city]? [tag=NN]? [tag=IN]? [word=its]? [tag=NN]? [tag=CC]? [tag=NN]? [tag=NN]? [word=and]? [tag=NNS]? [tag=IN]? ([tag=\",\"]? [tag=CC]?)+ ([word=□ | □] □ □)?"

//  val pattern ="[tag=NN | tag=NNS] [word=said]? [word=in]? [word=two]? [word=the]? [word=city]? [tag=NN]? [tag=IN]? [word=its]? [tag=NN]? [tag=CC]? [tag=NN]? [tag=NN]? [word=and]? [tag=NNS]? [tag=IN]? ([tag=\",\"]? [tag=CC]?)+"

  val pattern ="""[tag=NN] [word=said]? [word=in]? [word=two]? [word=the]? [word=city]? [tag=NN]? [tag=IN]? [word=its]? [tag=NN]? [tag=CC]? [tag=NN]? [tag=NN]? [word=and]? [tag=NNS]? [tag=IN]? [tag=","]? [tag=CC]? [tag=","]? [tag=CC]? ([tag=","]? [tag=CC]?)+ ([word=the])?"""

  "MatchSelector" should "handle pattern" in {
    val e1 = mkExtractorEngineFromText(t1.mkString(" "))
    val e2 = mkExtractorEngineFromText(t2.mkString(" "))
    val e3 = mkExtractorEngineFromText(t3.mkString(" "))
    val results1 = e1.query(e1.compiler.compile(pattern))
    val results2 = e2.query(e1.compiler.compile(pattern))
    val results3 = e3.query(e1.compiler.compile(pattern))
    val a = 1
    ()
  }





}
