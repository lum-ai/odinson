package ai.lum.odinson.utils

/** This class provides functionality similar to mutable.Map[K, V]'s getOrElseUpdate(key, default).
  * It keeps track of the (very) most recently used value associated with a key.
  * It's not a general cache or even a MoreRecentlyUsed, but is intended for use when keys
  * are either completely sorted or at least grouped so that == keys follow each other.
  * It is effectively a cache that holds a single value.  If differs from getOrElseUpdate()
  * in that the default value used in getOrElseUpdate() is stored in the constructor argument
  * of MostRecentlyUsed and not unnecessarily passed to getOrNew() each time.  The constructor
  * should use only the key to perform its task.
  * @param constructor a function that takes a key and returns a value, one that is probably newly constructed
  * @tparam K the type of the key
  * @tparam V the type of the value
  */
class MostRecentlyUsed[K, V](constructor: K => V) {
  var keyOpt: Option[K] = None
  var valueOpt: Option[V] = None

  /** @param key the key
    * @return if the key has just been seen, the value previously (constructed and) returned for the key,
    *         but otherwise a newly constructed (and then cached) value for the key
    */
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

  def apply[K, V](constructor: K => V): MostRecentlyUsed[K, V] =
    new MostRecentlyUsed(constructor)

}
