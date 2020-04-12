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
      // we will map some special characters to multichar strings
      val text = s
        .replaceAllLiterally("\u2122", "(TM)") // TRADE MARK SIGN
        .replaceAllLiterally("\u00c6", "AE")   // LATIN CAPITAL LETTER AE
        .replaceAllLiterally("\u00e6", "ae")   // LATIN SMALL LETTER AE
        .replaceAllLiterally("\u0152", "OE")   // LATIN CAPITAL LIGATURE OE
        .replaceAllLiterally("\u0153", "oe")   // LATIN SMALL LIGATURE OE
        .replaceAllLiterally("\u2021", "**")   // DOUBLE DAGGER
        .replaceAllLiterally("\u00ab", "<<")   // LEFT-POINTING DOUBLE ANGLE QUOTATION MARK
        .replaceAllLiterally("\u00bb", ">>")   // RIGHT-POINTING DOUBLE ANGLE QUOTATION MARK

        // TODO other mappings?
      // more agressive unicode normalization
      // case folding
      // diacritic removal
      nfkc_cf
        .normalize(text.stripAccents)
        // map char to char
        .replaceAllLiterally("\u2044", "/")  // FRACTION SLASH
        .replaceAllLiterally("\u201a", "'")  // SINGLE LOW-9 QUOTATION MARK
        .replaceAllLiterally("\u0192", "f")  // LATIN SMALL LETTER F WITH HOOK
        .replaceAllLiterally("\u201e", "\"") // DOUBLE LOW-9 QUOTATION MARK
        .replaceAllLiterally("\u2020", "*")  // DAGGER
        .replaceAllLiterally("\u02c6", "^")  // MODIFIER LETTER CIRCUMFLEX ACCENT
        .replaceAllLiterally("\u2039", "<")  // SINGLE LEFT-POINTING ANGLE QUOTATION MARK
        .replaceAllLiterally("\u2018", "'")  // LEFT SINGLE QUOTATION MARK
        .replaceAllLiterally("\u2019", "'")  // RIGHT SINGLE QUOTATION MARK
        .replaceAllLiterally("\u201c", "\"") // LEFT DOUBLE QUOTATION MARK
        .replaceAllLiterally("\u201d", "\"") // RIGHT DOUBLE QUOTATION MARK
        .replaceAllLiterally("\u2022", "-")  // BULLET
        .replaceAllLiterally("\u2013", "-")  // EN DASH
        .replaceAllLiterally("\u2014", "-")  // EM DASH
        .replaceAllLiterally("\u02dc", "~")  // SMALL TILDE
        .replaceAllLiterally("\u203a", ">")  // SINGLE RIGHT-POINTING ANGLE QUOTATION MARK
    } else {
      // unicode normalization
      // we use NFC as recommended by the W3C in https://www.w3.org/TR/charmod-norm/
      nfc.normalize(s)
    }
  }

}
