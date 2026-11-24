package com.example.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Formats pure digit input into Indonesian thousand-separated currency display
 * (e.g., "1500000" -> "1.500.000") while keeping underlying state as raw digits.
 */
class RupiahVisualTransformation(
    private val separator: Char = '.'
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        if (original.isEmpty()) {
            return TransformedText(AnnotatedString(""), OffsetMapping.Identity)
        }

        val n = original.length
        val sb = StringBuilder(n + n / 3)
        val origToTrans = IntArray(n + 1)
        val transToOrig = ArrayList<Int>(n + n / 3 + 1)

        for (i in 0 until n) {
            if (i > 0 && (n - i) % 3 == 0) {
                sb.append(separator)
                transToOrig.add(i)
            }
            origToTrans[i] = sb.length
            sb.append(original[i])
            transToOrig.add(i)
        }
        origToTrans[n] = sb.length
        transToOrig.add(n)

        val formatted = sb.toString()
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return origToTrans[offset.coerceIn(0, n)]
            }

            override fun transformedToOriginal(offset: Int): Int {
                return transToOrig[offset.coerceIn(0, formatted.length)]
            }
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}

fun sanitizeRupiahDigits(raw: String, maxDigits: Int = 12): String {
    return raw.filter(Char::isDigit).trimStart('0').take(maxDigits)
}
