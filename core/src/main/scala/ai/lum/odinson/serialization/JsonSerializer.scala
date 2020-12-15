package ai.lum.odinson.serialization

import ai.lum.odinson._
import ujson.Value

object JsonSerializer {

  // Json String

  def asJsonPretty(ms: Iterator[Mention]): String = {
    asJsonPretty(ms, indent = 4)
  }

  def asJsonPretty(ms: Iterator[Mention], indent: Int): String = {
    asJsonPretty(ms.toSeq, indent)
  }

  def asJsonPretty(ms: Seq[Mention]): String = {
    asJsonPretty(ms, indent = 4)
  }

  def asJsonPretty(ms: Seq[Mention], indent: Int): String = {
    ujson.write(asJsonValue(ms), indent)
  }

  def asJsonPretty(m: Mention): String = {
    ujson.write(asJsonValue(m), indent = 4)
  }

  def asJsonPretty(m: Mention, indent: Int): String = {
    ujson.write(asJsonValue(m), indent)
  }

  def asJsonString(ms: Iterator[Mention]): String = {
    asJsonString(ms.toSeq)
  }

  def asJsonString(ms: Seq[Mention]): String = {
    ujson.write(asJsonValue(ms))
  }

  def asJsonString(m: Mention): String = {
    ujson.write(asJsonValue(m))
  }


  // Json Lines (one mention json per line)

  def asJsonLines(ms: Iterator[Mention]): Seq[String] = {
    asJsonLines(ms.toSeq)
  }

  def asJsonLines(ms: Seq[Mention]): Seq[String] = {
    ms.map(asJsonString)
  }

  // Json Value

  def asJsonValue(ms: Iterator[Mention]): Value = {
    asJsonValue(ms.toSeq)
  }

  def asJsonValue(ms: Seq[Mention]): Value = {
    ms.map(asJsonValue)
  }

  def asJsonValue(m: Mention): Value = {
    val corpusDocId = m.idGetter.getDocId
    val corpusSentId = m.idGetter.getSentId

    ujson.Obj (
      "scalaType" -> m.getClass.getCanonicalName,
      "odinsonMatch" -> asJsonValue(m.odinsonMatch),
      "label" -> stringOrNull(m.label),
      "luceneDocId" -> m.luceneDocId,
      "luceneSegmentDocId" -> m.luceneSegmentDocId,
      "luceneSegmentDocBase" -> m.luceneSegmentDocBase,
      "docId" -> corpusDocId,
      "sentId" -> corpusSentId,
      "foundBy" -> m.foundBy,
      "arguments" -> asJsonValue(m.arguments)
    )
  }

  def asJsonValue(om: OdinsonMatch): Value = {
    val scalaType = om.getClass.getCanonicalName
     om match {
      case sm: StateMatch =>
        ujson.Obj(
          "scalaType" -> scalaType,
          "start" -> sm.start,
          "end" -> sm.end,
          "namedCaptures" -> sm.namedCaptures.map(asJsonValue).toSeq
        )
      case ng: NGramMatch =>
        ujson.Obj(
          "scalaType" -> scalaType,
          "start" -> ng.start,
          "end" -> ng.end,
        )
      case em: EventMatch =>
        ujson.Obj(
          "scalaType" -> scalaType,
          "trigger" -> asJsonValue(em.trigger),
          "namedCaptures" -> em.namedCaptures.map(asJsonValue).toSeq,
          "argMetadata" -> asJsonValue(em.argumentMetadata)
        )

      case gt: GraphTraversalMatch =>
        ujson.Obj(
          "scalaType" -> scalaType,
          "srcMatch" -> asJsonValue(gt.srcMatch),
          "dstMatch" -> asJsonValue(gt.dstMatch),
        )

      case concat: ConcatMatch =>
      ujson.Obj(
        "scalaType" -> scalaType,
        "subMatches" -> concat.subMatches.map(asJsonValue).toSeq,
      )

      case rep: RepetitionMatch =>
        ujson.Obj(
          "scalaType" -> scalaType,
          "subMatches" -> rep.subMatches.map(asJsonValue).toSeq,
          "isGreedy" -> rep.isGreedy,
        )

      case opt: OptionalMatch =>
        ujson.Obj(
          "scalaType" -> scalaType,
          "subMatch" -> asJsonValue(opt.subMatch),
          "isGreedy" -> opt.isGreedy,
        )

      case or: OrMatch =>
        ujson.Obj(
          "scalaType" -> scalaType,
          "subMatch" -> asJsonValue(or.subMatch),
          "clauseID" -> or.clauseID
        )

      case named: NamedMatch =>
        ujson.Obj(
          "scalaType" -> scalaType,
          "subMatch" -> asJsonValue(named.subMatch),
          "name" -> named.name,
          "label" -> stringOrNull(named.label),
        )

      case _ => ???
    }
  }

  def asJsonValue(n: NamedCapture): Value = {
    ujson.Obj(
      "scalaType" -> n.getClass.getCanonicalName,
      "name" -> n.name,
      "label" -> stringOrNull(n.label),
      "capturedMatch" -> asJsonValue(n.capturedMatch)
    )
  }

  def asJsonValue(arguments: Map[String, Array[Mention]]): Value = {
    val json = ujson.Obj()
    arguments.toIterator.foreach { case (argName, mentions) =>
      json(argName) = mentions.map(asJsonValue).toSeq
    }
    json
  }

  def asJsonValue(metadatas: Array[ArgumentMetadata]): Value = {
    ujson.Arr{
      metadatas.map(asJsonValue).toSeq
    }
  }

  def asJsonValue(metadata: ArgumentMetadata): Value = {
    ujson.Obj(
      "name" -> metadata.name,
      "min" -> metadata.min,
      "max" -> intOrNull(metadata.max)
    )
  }

  def stringOrNull(s: Option[String]): Value = {
    if (s.isDefined) ujson.Str(s.get)
    else ujson.Null
  }

  def intOrNull(i: Option[Int]): Value = {
    if (i.isDefined) ujson.Num(i.get)
    else ujson.Null
  }

  // Deserialization

  // Deserialize from String

  def deserializeMentions(json: String): Array[Mention] = {
    ujson.read(json).arr.map(deserializeMention).toArray
  }

  def deserializeMention(json: String): Mention = {
    deserializeMention(ujson.read(json))
  }

  // Deserialize JsonLines

  def deserializeJsonLines(lines: Seq[String]): Array[Mention] = {
    deserializeJsonLines(lines.toArray)
  }

  def deserializeJsonLines(lines: Array[String]): Array[Mention] = {
    lines.map(deserializeMention)
  }

  // Deserialize from Json Value

  // Method to call when deserializing json of Array of Mentions, as such,
  // this is likely the most common "entry point"
  def deserializeMentions(json: Value): Array[Mention] = {
    json.arr.map(deserializeMention).toArray
  }

  def deserializeMention(json: Value): Mention = {
    val scalaType = json("scalaType").str
    require(scalaType == "ai.lum.odinson.Mention")

    val odinsonMatch = deserializeMatch(json("odinsonMatch"))
    val label = deserializeOptionString(json("label"))
    val luceneDocId = json("luceneDocId").num.toInt
    val luceneSegmentDocId = json("luceneSegmentDocId").num.toInt
    val luceneSegmentDocBase = json("luceneSegmentDocBase").num.toInt
    val foundBy = json("foundBy").str
    val arguments = deserializeArguments(json("arguments"))

    // Make an idGetter
    val docId = json("docId").str
    val sentId = json("sentId").str
    val idGetter = new KnownIdGetter(docId, sentId)

    new Mention(odinsonMatch, label, luceneDocId, luceneSegmentDocId, luceneSegmentDocBase, idGetter, foundBy, arguments)
  }

  def deserializeMatch(json: Value): OdinsonMatch = {

    json("scalaType").str match {

      case "ai.lum.odinson.StateMatch" =>
        val start = json("start").num.toInt
        val end = json("end").num.toInt
        val namedCaptures = deserializeNamedCaptures(json("namedCaptures"))
        new StateMatch(start, end, namedCaptures)

      case "ai.lum.odinson.NGramMatch" =>
        val start = json("start").num.toInt
        val end = json("end").num.toInt
        new NGramMatch(start, end)

      case "ai.lum.odinson.EventMatch" =>
        val trigger = deserializeMatch(json("trigger"))
        val namedCaptures = deserializeNamedCaptures(json("namedCaptures"))
        val argumentMetadata = deserializeArgMetadatas(json("argMetadata"))
        new EventMatch(trigger, namedCaptures, argumentMetadata)

      case "ai.lum.odinson.GraphTraversalMatch" =>
        val srcMatch = deserializeMatch(json("srcMatch"))
        val dstMatch = deserializeMatch(json("dstMatch"))
        new GraphTraversalMatch(srcMatch, dstMatch)

      case "ai.lum.odinson.ConcatMatch" =>
        val subMatches = json("subMatches").arr.toArray.map(deserializeMatch)
        new ConcatMatch(subMatches)

      case "ai.lum.odinson.RepetitionMatch" =>
        val subMatches = json("subMatches").arr.toArray.map(deserializeMatch)
        val isGreedy = json("isGreedy").bool
        new RepetitionMatch(subMatches, isGreedy)

      case "ai.lum.odinson.OptionalMatch" =>
        val subMatch = deserializeMatch(json("subMatch"))
        val isGreedy = json("isGreedy").bool
        new OptionalMatch(subMatch, isGreedy)

      case "ai.lum.odinson.OrMatch" =>
        val subMatch = deserializeMatch(json("subMatch"))
        val clauseID = json("clauseID").num.toInt
        new OrMatch(subMatch, clauseID)

      case "ai.lum.odinson.NamedMatch" =>
        val subMatch = deserializeMatch(json("subMatch"))
        val name = json("name").str
        val label = deserializeOptionString(json("label"))
        new NamedMatch(subMatch, name, label)

      case _ => ???
    }
  }

  def deserializeArguments(json: Value): Map[String, Array[Mention]] = {
    json.obj.mapValues(deserializeMentions).toMap
  }

  def deserializeNamedCaptures(json: Value): Array[NamedCapture] = {
    json.arr.toArray.map(deserializeNamedCapture)
  }
  def deserializeNamedCapture(json: Value): NamedCapture = {
    val name = json("name").str
    val label = deserializeOptionString(json("label"))
    val capturedMatch = deserializeMatch(json("capturedMatch"))
    NamedCapture(name, label, capturedMatch)
  }

  def deserializeArgMetadatas(json: Value): Array[ArgumentMetadata] = {
    json.arr.toArray.map(deserializeArgMetadata)
  }
  def deserializeArgMetadata(json: Value): ArgumentMetadata = {
    val metadata = json.obj.toMap
    val name = metadata("name").str
    val min = metadata("min").num.toInt
    val max = deserializeOptionInt(metadata("max"))
    val promote = metadata("promote").bool
    ArgumentMetadata(name, min, max, promote)
  }

  def deserializeOptionString(json: Value): Option[String] = {
    json match {
      case ujson.Null => None
      case someLabel => Some(someLabel.str)
    }
  }

  def deserializeOptionInt(json: Value): Option[Int] = {
    json match {
      case ujson.Null => None
      case i => Some(i.num.toInt)
    }
  }

}
