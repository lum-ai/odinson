package ai.lum.odinson.utils

import com.ibm.icu.text.Normalizer2
import ai.lum.common.StringUtils._

object Normalizer {

  private val nfc = Normalizer2.getNFCInstance()
  private val nfkc_cf = Normalizer2.getNFKCCasefoldInstance()

  /** string normalization */
  def normalize(s: String): String = {
    normalize(s, aggressive = false)
  }

  /** string normalization */
  def normalize(s: String, aggressive: Boolean): String = {
    if (aggressive) {
      // more agressive unicode normalization
      // case folding
      // diacritic removal
      nfkc_cf
        .normalize(s.stripAccents)
        .replace("\u2044", "/") // FRACTION SLASH
    } else {
      // unicode normalization
      // we use NFC as recommended by the W3C in https://www.w3.org/TR/charmod-norm/
      nfc.normalize(s)
    }
  }

}
