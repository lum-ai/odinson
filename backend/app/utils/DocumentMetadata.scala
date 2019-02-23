package utils

/**
  * Document Metadata. <br>
  * FIXME: Duplicates Metadata in odin.extras, but extras shouldn't really be a dependency of the webapp...
  */
case class DocumentMetadata(
  docId: String,
  authors: Option[Seq[String]] = None,
  title: Option[String] = None,
  doi: Option[String] = None,
  url: Option[String] = None,
  year: Option[Int] = None,
  venue: Option[String] = None
)