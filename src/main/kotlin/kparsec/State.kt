package kparsec

data class State<I, E, T>(
    val input: I,
    val offset: Int,
    val posState: PosState<I>,
    val parseErrors: List<ParsecError<E, T>>
) {
    fun longestMatch(s: State<I, E, T>): State<I, E, T> =
        if (offset <= s.offset) s
        else this

    companion object {
        fun <I, E, T> initialState(name: String, inp: I): State<I, E, T> =
            State(
                inp, 0,
                PosState(inp, 0, SourcePos(name, 1, 1), "", 8),
                emptyList()
            )
    }
}