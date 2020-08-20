package ai.lum.odinson.utils

class MostRecentlyUsed[K, V](constructor: K => V) {
  var keyOpt: Option[K] = None
  var valueOpt: Option[V] = None

  def getOrNew(key: K): V = {
    if (keyOpt.contains(key))
      valueOpt.get
    else {
      val value = constructor(key)

      keyOpt = Some(key)
      valueOpt = Some(value)
      value
    }
  }
}

object MostRecentlyUsed {

  def apply[K, V](constructor: K => V): MostRecentlyUsed[K, V] = new MostRecentlyUsed(constructor)
}
