package kparsec

import arrow.core.*
import arrow.core.extensions.list.foldable.foldLeft
import pretty.*
import pretty.symbols.colon
import pretty.symbols.pipe

sealed class ParsecError<out E, out T> {
    data class Trivial<T>(
        val offset: Int,
        val unexpectedTokens: Option<ErrorItem<T>> = None,
        val expectedTokens: Set<ErrorItem<T>> = emptySet()
    ) : ParsecError<Nothing, T>() {
        fun setUnexpectedToken(tok: Nel<T>): Trivial<T> = Trivial(offset, ErrorItem.Tokens(tok).some(), expectedTokens)
        fun setUnexpectedLabel(label: String): Trivial<T> = Trivial(offset, ErrorItem.Label(label).some(), expectedTokens)
        fun setUnexpectedEndOfFile(): Trivial<T> = Trivial(offset, ErrorItem.EndOfInput.some(), expectedTokens)

        fun addExpectedToken(tok: Nel<T>): Trivial<T> = Trivial(offset, unexpectedTokens, expectedTokens.plus(ErrorItem.Tokens(tok)))
        fun addExpectedTokens(tokens: Set<Nel<T>>): Trivial<T> = Trivial(offset, unexpectedTokens, expectedTokens.plus(tokens.map { ErrorItem.Tokens(it) }))
        fun addExpectedLabel(label: String): Trivial<T> = Trivial(offset, unexpectedTokens, expectedTokens.plus(ErrorItem.Label(label)))
        fun addExpectedEndOfFile(): Trivial<T> = Trivial(offset, unexpectedTokens, expectedTokens.plus(ErrorItem.EndOfInput))
    }

    // megaparsec has two more cases of this where one is for fail, which we don't need, and the other is for indentation which I may add if requested...
    data class Fancy<E>(
        val offset: Int,
        val errors: Set<E> = emptySet()
    ) : ParsecError<E, Nothing>() {
        fun addError(e: E): Fancy<E> = Fancy(offset, errors.plus(e))
        fun addErrors(e: Set<E>): Fancy<E> = Fancy(offset, errors.plus(e))
    }

    fun offset(): Int = when (this) {
        is Trivial -> offset
        is Fancy -> offset
    }
}

fun <E, T> ParsecError<E, T>.toHints(currOff: Int): Hints<T> = when (this) {
    is ParsecError.Trivial ->
        if (currOff == offset) Hints(if (expectedTokens.isEmpty()) emptyList() else listOf(expectedTokens))
        else Hints.empty()
    is ParsecError.Fancy -> Hints.empty()
}

fun <E, T> ParsecError<E, T>.withHints(h: Hints<T>): ParsecError<E, T> = when (this) {
    is ParsecError.Trivial -> ParsecError.Trivial(
        offset,
        unexpectedTokens,
        expectedTokens.union(h.hints.foldLeft(emptySet()) { acc, v -> acc.union(v) })
    )
    is ParsecError.Fancy -> this
}

operator fun <E, T> ParsecError<E, T>.plus(other: ParsecError<E, T>): ParsecError<E, T> {
    val lOff = offset()
    val rOff = other.offset()

    return when {
        lOff < rOff -> other
        lOff > rOff -> this
        else -> when (this) {
            is ParsecError.Trivial -> when (other) {
                is ParsecError.Trivial -> ParsecError.Trivial(
                    offset,
                    unexpectedTokens.or(other.unexpectedTokens),
                    expectedTokens.union(other.expectedTokens)
                )
                is ParsecError.Fancy -> other
            }
            is ParsecError.Fancy -> when (other) {
                is ParsecError.Trivial -> this
                is ParsecError.Fancy -> ParsecError.Fancy(offset, errors.union(other.errors))
            }
        }
    }
}

sealed class ErrorItem<out T> {
    data class Tokens<T>(val ts: NonEmptyList<T>) : ErrorItem<T>()
    data class Label(val ts: String) : ErrorItem<Nothing>()
    object EndOfInput: ErrorItem<Nothing>()
}

fun <S, T, CHUNK> ErrorItem<T>.length(SI: Stream<S, T, CHUNK>): Int = when (this) {
    is ErrorItem.Tokens -> SI.run { ts.tokenLength() }
    else -> 1
}

data class ParseErrorBundle<E, I, T>(
    val bundleErrors: Nel<ParsecError<E, T>>,
    val bundlePosState: PosState<I>
)

data class PosState<I>(
    val input: I,
    val offset: Int,
    val sourcePos: SourcePos,
    val linePre: String,
    val tabWidth: Int
)

data class SourcePos(
    val name: String,
    val line: Int,
    val column: Int
) {
    fun doc(): Doc<Nothing> {
        val lc = line.doc() + colon() + column.doc()
        return if (name.isEmpty()) lc else name.text() + colon() + lc
    }
}

fun <E, I, T> Nel<ParsecError<E, T>>.toBundle(s: State<I, E, T>): ParseErrorBundle<E, I, T> =
    ParseErrorBundle(
        Nel.fromListUnsafe(all.sortedBy { it.offset() }), s.posState
    )

fun <E, EL, I, CHUNK> ParseErrorBundle<E, I, EL>.renderPretty(SI: Stream<I, EL, CHUNK>, renderE: (E) -> Doc<Nothing> = { it.toString().text() }): String =
    bundleErrors.foldLeft(emptyList<Doc<Nothing>>() toT bundlePosState) { (xs, pos), v ->
        val (sline, pst) = SI.reachOffset(v.offset(), pos)
        val epos = pst.sourcePos
        val lineNr = epos.line.toString()
        val padding = spaces(lineNr.length + 1).text()
        val rpShift = epos.column - 1
        val elen = when (v) {
            is ParsecError.Trivial -> v.unexpectedTokens.fold({ 1 }, { it.length(SI) })
            is ParsecError.Fancy -> 1 // TODO implement this better
        }
        val pointerLen = if (rpShift + elen > sline.length) sline.length - rpShift + 1 else elen
        val pointer = (1..pointerLen).fold(StringBuilder()) { acc, _ -> acc.append("^") }.toString().text()
        val rPadding = if (pointerLen > 0) spaces(rpShift).text() else nil()
        val chunk = epos.doc() + colon() + hardLine() + padding + pipe() + hardLine() +
                lineNr.doc() spaced pipe() spaced sline.text() + hardLine() +
                padding + pipe() spaced rPadding + pointer + hardLine() +
                v.errorText(SI, renderE)

        xs + listOf(chunk) toT pst
    }.a.vCat()
        .layoutPretty(PageWidth.Available(120, 0.5f)).renderString()

fun spaces(nr: Int): String = generateSequence { ' ' }.take(nr).joinToString("")

fun <E, EL, I, CHUNK> ParsecError<E, EL>.errorDoc(SI: Stream<I, EL, CHUNK>, renderE: (E) -> Doc<Nothing> = { it.toString().text() }): Doc<Nothing> =
    "offset=".text() spaced offset().doc() + hardLine() + errorText(SI, renderE)

fun <E, EL, I, CHUNK> ParsecError<E, EL>.errorText(SI: Stream<I, EL, CHUNK>, renderE: (E) -> Doc<Nothing> = { it.toString().text() }): Doc<Nothing> = when (this) {
    is ParsecError.Trivial -> if (unexpectedTokens.isEmpty() && expectedTokens.isEmpty()) "unknown parse error".text() else
        ("unexpected:".text() softLine unexpectedTokens.fold({ nil() }, { t -> t.showPretty(SI) })).hang(4) + hardLine() +
                ("expecting:".text() softLine expectedTokens.toList().map { it.showPretty(SI) }.punctuate(" or ".text().flatAlt(" or".text())).sep()).hang(4)
    is ParsecError.Fancy -> if (errors.isEmpty()) "unknown fancy parse error".text() else
        errors.toList().map(renderE).vCat()
}

fun <S, EL, CHUNK> ErrorItem<EL>.showPretty(SI: Stream<S, EL, CHUNK>): Doc<Nothing> = when (this) {
    is ErrorItem.Tokens -> SI.run { ts.show().text() }
    is ErrorItem.Label -> ts.text()
    is ErrorItem.EndOfInput -> "end of input".text()
}
