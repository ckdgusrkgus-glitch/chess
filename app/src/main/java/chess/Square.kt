package chess

data class Square(val file: Int, val rank: Int) {
    val index: Int get() = rank * 8 + file
    val isValid: Boolean get() = file in 0..7 && rank in 0..7

    fun offset(deltaFile: Int, deltaRank: Int) = Square(file + deltaFile, rank + deltaRank)

    override fun toString(): String = "${'a' + file}${rank + 1}"

    companion object {
        fun fromIndex(index: Int) = Square(index % 8, index / 8)

        fun fromAlgebraic(s: String): Square? {
            if (s.length != 2) return null
            val file = s[0].lowercaseChar() - 'a'
            val rank = s[1] - '1'
            val sq = Square(file, rank)
            return if (sq.isValid) sq else null
        }
    }
}
