package kparsec

import arrow.core.*
import arrow.core.extensions.eq
import arrow.typeclasses.Eq
import pretty.spaces
import kotlin.math.max

// Input typeclass to provide options to implement this for other text types (and streaming text)
/**
 * S is the input type
 * EL a single element of the stream
 * CHUNK a single chunk of the stream
 * TODO List<EL> -> String with pretty printing chars
 */
interface Stream<S, EL, CHUNK> {
    fun EQEL(): Eq<EL>
    fun EQCHUNK(): Eq<CHUNK>

    fun S.takeOne(): Option<Tuple2<EL, S>>
    fun S.takeWhile(p: (EL) -> Boolean): Tuple2<CHUNK, S>
    fun S.take(i: Int): Option<Tuple2<CHUNK, S>>

    fun CHUNK.isEmpty(): Boolean
    fun CHUNK.size(): Int

    fun List<EL>.toChunk(): CHUNK
    fun CHUNK.toTokens(): List<EL>

    fun reachOffset(off: Int, init: PosState<S>): Tuple2<String, PosState<S>>
    fun reachOffsetNoNewline(off: Int, init: PosState<S>): PosState<S> = reachOffset(off, init).b

    fun List<EL>.show(): String
    fun List<EL>.tokenLength(): Int = size
}

fun <S, EL, CHUNK> Stream<S, EL, CHUNK>.reachOffset(
    splitAt: (Int, S) -> Tuple2<CHUNK, S>,
    fold: ((Any?, EL) -> Any?, Any?, CHUNK) -> Any?,
    newline: EL, tab: EL,
    offset: Int,
    initial: PosState<S>
): Tuple2<String, PosState<S>> {
    val (pre, post) = splitAt(offset - initial.offset, initial.input)
    val (spos, str) = fold({ acc, el ->
        val (sp, str) = acc as Tuple2<SourcePos, String>
        EQEL().run {
            if (el.eqv(newline)) SourcePos(sp.name, sp.line + 1, sp.column + 1) toT str
            else if (el.eqv(tab))
                SourcePos(
                    sp.name,
                    sp.line,
                    sp.column + initial.tabWidth - (sp.column - 1).rem(initial.tabWidth)
                ) toT str + listOf(el).show()
            else SourcePos(sp.name, sp.line, sp.column + 1) toT str + listOf(el).show()
        }
    }, (initial.sourcePos toT ""), pre) as Tuple2<SourcePos, String>
    val sameLine = spos.line == initial.sourcePos.line
    fun addPrefix(str: String): String = if (sameLine) initial.linePre + str else str

    val strRes = addPrefix(
        str + post.takeWhile { EQEL().run { it.neqv(newline) } }.a
            .toTokens()
            .show()
    ).expandTab(initial.tabWidth)

    val posRes = PosState(
        post, max(offset, initial.offset),
        spos, if (sameLine) initial.linePre + str else str,
        initial.tabWidth
    )

    return strRes toT posRes
}

fun <S, EL, CHUNK> Stream<S, EL, CHUNK>.reachOffsetNoNewline(
    splitAt: (Int, S) -> Tuple2<CHUNK, S>,
    fold: ((Any?, EL) -> Any?, Any?, CHUNK) -> Any?,
    newline: EL, tab: EL,
    offset: Int,
    initial: PosState<S>
): PosState<S> {
    val (pre, post) = splitAt(offset - initial.offset, initial.input)
    val spos = fold({ sp, el ->
        sp as SourcePos
        EQEL().run {
            if (el.eqv(newline)) SourcePos(sp.name, sp.line + 1, sp.column + 1)
            else if (el.eqv(tab))
                SourcePos(
                    sp.name,
                    sp.line,
                    sp.column + initial.tabWidth - (sp.column - 1).rem(initial.tabWidth)
                )
            else SourcePos(sp.name, sp.line, sp.column + 1)
        }
    }, initial.sourcePos, pre) as SourcePos

    return PosState(
        post, max(offset, initial.offset), spos, initial.linePre, initial.tabWidth
    )
}

fun String.expandTab(w: Int): String = replace("\t", spaces(w))

// ---------------------------------- Instances -----------------------------------------

interface StringStream : Stream<String, Char, String> {
    override fun EQCHUNK(): Eq<String> = String.eq()
    override fun EQEL(): Eq<Char> = Char.eq()

    override fun String.isEmpty(): Boolean = length == 0
    override fun String.size(): Int = length

    override fun String.take(i: Int): Option<Tuple2<String, String>> = when {
        i <= 0 -> ("" toT this).some()
        length == 0 -> None
        else -> (this.substring(0, kotlin.math.min(i, length - 1)) toT this.substring(kotlin.math.min(i, length - 1))).some()
    }

    override fun String.takeOne(): Option<Tuple2<Char, String>> =
        if (length == 0) None
        else (first() toT drop(1)).some()

    override fun String.takeWhile(p: (Char) -> Boolean): Tuple2<String, String> =
        takeWhile_(p).let { match -> (match toT this.substring(match.length)) }

    override fun List<Char>.toChunk(): String = String(toCharArray())
    override fun String.toTokens(): List<Char> = toCharArray().toList()

    override fun List<Char>.show(): String = joinToString("")

    override fun reachOffset(off: Int, init: PosState<String>): Tuple2<String, PosState<String>> =
        reachOffset({ n, i ->
            if (n < 0) "" toT i
            else i.substring(0, n) toT i.substring(n)
        }, { f, n, s -> s.fold(n, f) }, '\n', '\t', off, init)

    override fun reachOffsetNoNewline(off: Int, init: PosState<String>): PosState<String> =
        reachOffsetNoNewline({ n, i ->
            if (n < 0) "" toT i
            else i.substring(0, n) toT i.substring(n)
        }, { f, n, s -> s.fold(n, f) }, '\n', '\t', off, init)
}

fun String.Companion.stream(): Stream<String, Char, String> = object : StringStream {}

private inline fun String.takeWhile_(p: (Char) -> Boolean): String =
    takeWhile(p)