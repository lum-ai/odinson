package ai.lum.odinson.serialization

import ai.lum.odinson._
import ujson.Value

object JsonSerializer {

  def jsonify(ms: Iterator[Mention]): Value = {
    jsonify(ms.toSeq)
  }

  def jsonify(ms: Seq[Mention]): Value = {
    ms.map(jsonify)
  }

  def jsonify(m: Mention): Value = {
    val corpusDocId = m.idGetter.getDocId
    val corpusSentId = m.idGetter.getSentId

    ujson.Obj (
      "scalaType" -> m.getClass.getCanonicalName,
      "odinsonMatch" -> jsonify(m.odinsonMatch),
      "label" -> stringOrNull(m.label),
      "luceneDocId" -> m.luceneDocId,
      "luceneSegmentDocId" -> m.luceneSegmentDocId,
      "luceneSegmentDocBase" -> m.luceneSegmentDocBase,
      "docId" -> corpusDocId,
      "sentId" -> corpusSentId,
      "foundBy" -> m.foundBy,
      "arguments" -> jsonify(m.arguments)
    )
  }

  def jsonify(om: OdinsonMatch): Value = {
    val scalaType = om.getClass.getCanonicalName
     om match {
      case sm: StateMatch =>
        ujson.Obj(
          "scalaType" -> scalaType,
          "start" -> sm.start,
          "end" -> sm.end,
          "namedCaptures" -> sm.namedCaptures.map(jsonify).toSeq
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
          "trigger" -> jsonify(em.trigger),
          "namedCaptures" -> em.namedCaptures.map(jsonify).toSeq,
          "argMetadata" -> jsonify(em.argumentMetadata)
        )

      case gt: GraphTraversalMatch =>
        ujson.Obj(
          "scalaType" -> scalaType,
          "srcMatch" -> jsonify(gt.srcMatch),
          "dstMatch" -> jsonify(gt.dstMatch),
        )

      case concat: ConcatMatch =>
      ujson.Obj(
        "scalaType" -> scalaType,
        "subMatches" -> concat.subMatches.map(jsonify).toSeq,
      )

      case rep: RepetitionMatch =>
        ujson.Obj(
          "scalaType" -> scalaType,
          "subMatches" -> rep.subMatches.map(jsonify).toSeq,
          "isGreedy" -> rep.isGreedy,
        )

      case opt: OptionalMatch =>
        ujson.Obj(
          "scalaType" -> scalaType,
          "subMatch" -> jsonify(opt.subMatch),
          "isGreedy" -> opt.isGreedy,
        )

      case or: OrMatch =>
        ujson.Obj(
          "scalaType" -> scalaType,
          "subMatch" -> jsonify(or.subMatch),
          "clauseID" -> or.clauseID
        )

      case named: NamedMatch =>
        ujson.Obj(
          "scalaType" -> scalaType,
          "subMatch" -> jsonify(named.subMatch),
          "name" -> named.name,
          "label" -> stringOrNull(named.label),
        )

      case _ => ???
    }
  }

  def jsonify(n: NamedCapture): Value = {
    ujson.Obj(
      "scalaType" -> n.getClass.getCanonicalName,
      "name" -> n.name,
      "label" -> stringOrNull(n.label),
      "capturedMatch" -> jsonify(n.capturedMatch)
    )
  }

  def jsonify(arguments: Map[String, Array[Mention]]): Value = {
    ujson.Arr(
      arguments.mapValues(_.map(jsonify).toSeq)
    )
  }

  def jsonify(metadatas: Array[ArgumentMetadata]): Value = {
    ujson.Arr{
      metadatas.map(jsonify).toSeq
    }
  }

  def jsonify(metadata: ArgumentMetadata): Value = {
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

  // Method to call when deserializing an Array of Mentions, as such,
  // this is likely the most common "entry point"
  def deserialize(json: Value): Array[Mention] = {
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
    // The serialization of the Map is in the first (and only) element of an ArrayBuffer,
    // hence the arr.headOption
    json.arr.headOption match {
      case Some(empty) if empty.obj.isEmpty => Map.empty[String, Array[Mention]]
      case Some(args) => args.obj.mapValues(deserialize).toMap
      case None => Map.empty[String, Array[Mention]]
    }
  }

  def deserializeNamedCaptures(json: Value): Array[NamedCapture] = {
    json.arr.toArray.map(deserializeNamedCapture)
  }
  def deserializeNamedCapture(json: Value): NamedCapture = {
    val name = json("name").str
    val label = deserializeOptionString(json("label").str)
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
