package ai.lum.odinson.utils

import scala.reflect.ClassTag

object ArrayUtils {

  def merge[A: ClassTag](a1: Array[A], a2: Array[A]): Array[A] = {
    val length = a1.length + a2.length
    val result = new Array[A](length)
    System.arraycopy(a1, 0, result, 0, a1.length)
    System.arraycopy(a2, 0, result, a1.length, a2.length)
    result
  }

  def append[A: ClassTag](a: Array[A], elem: A): Array[A] = {
    val length = a.length + 1
    val result = new Array[A](length)
    System.arraycopy(a, 0, result, 0, a.length)
    result(a.length) = elem
    result
  }

  def prepend[A: ClassTag](a: Array[A], elem: A): Array[A] = {
    val length = a.length + 1
    val result = new Array[A](length)
    result(0) = elem
    System.arraycopy(a, 0, result, 1, a.length)
    result
  }

}
