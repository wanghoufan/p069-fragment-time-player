package com.wanghoufan.fragmentplayer.importer

object NaturalSort : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        if (a === b) return 0
        val aLength = a.length
        val bLength = b.length
        var i = 0
        var j = 0

        while (i < aLength && j < bLength) {
            val aChar = a[i]
            val bChar = b[j]
            if (aChar.isDigit() && bChar.isDigit()) {
                val aStart = i
                while (i < aLength && a[i].isDigit()) i++
                val bStart = j
                while (j < bLength && b[j].isDigit()) j++
                val aDigits = a.substring(aStart, i).trimStart('0')
                val bDigits = b.substring(bStart, j).trimStart('0')
                val byValue = compareDigits(aDigits, bDigits)
                if (byValue != 0) return byValue
            } else {
                if (aChar != bChar) return aChar.compareTo(bChar)
                i++
                j++
            }
        }

        val rest = (aLength - i) - (bLength - j)
        if (rest != 0) return rest
        return a.compareTo(b)
    }

    private fun compareDigits(a: String, b: String): Int {
        if (a.length != b.length) return a.length - b.length
        return a.compareTo(b)
    }
}