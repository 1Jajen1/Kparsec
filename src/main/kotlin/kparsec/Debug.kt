package kparsec

import arrow.core.Nel
import arrow.core.toT
import arrow.typeclasses.Show
import pretty.*

fun <E, I, EL, CHUNK, M, A> KParsecTOf<E, I, EL, M, A>.dbg(
    label: String,
    SI: Stream<I, EL, CHUNK>,
    SA: Show<A> = Show.any(),
    renderE: (E) -> Doc<Nothing> = { toString().text() }
): KParsecT<E, I, EL, M, A> = fix().run {
    KParsecT(MM) { s ->
        MM.run {
            SI.run { input(label, s.input.take(40).map { it.a }.fold({ emptyList<EL>() }, { it.toTokens() })) }
                .let(::println)
            pFun(s).map { (pa, s1) ->
                (when (pa) {
                    is ParserState.ConsumedOk -> SI.cOk(label, SI.delta(s, s1), pa.a, SA)
                    is ParserState.EmptyOk -> SI.eOk(label, SI.delta(s, s1), pa.a, SA)
                    is ParserState.ConsumedError -> SI.cErr(label, SI.delta(s, s1), pa.e, renderE)
                    is ParserState.EmptyError -> SI.eErr(label, SI.delta(s, s1), pa.e, renderE)
                } + "\n").let(::println)
                pa toT s1
            }
        }
    }
}

fun <E, S, EL, CHUNK> Stream<S, EL, CHUNK>.delta(s: State<S, E, EL>, s1: State<S, E, EL>): List<EL> =
    (s1.offset - s.offset).let { d -> s.input.take(d).fold({ emptyList() }, { it.a.toTokens() }) }

fun <S, EL, CHUNK> Stream<S, EL, CHUNK>.input(label: String, c: List<EL>): String =
    "$label> IN: " + Nel.fromList(c).fold({ "<EMPTY>" }, { it.show().let { if (it.length > 40) it.substring(0, 40) + "<..>" else it } })

fun <S, EL, CHUNK, A> Stream<S, EL, CHUNK>.cOk(label: String, c: List<EL>, a: A, SA: Show<A>): String =
    "$label> MATCH (COK): " + Nel.fromList(c).fold({ "<EMPTY>" }, { it.show().let { if (it.length > 40) it.substring(0, 40) + "<..>" else it } }) + "\n" +
            "$label> VALUE: " + SA.run { a.show() }

fun <S, EL, CHUNK, A> Stream<S, EL, CHUNK>.eOk(label: String, c: List<EL>, a: A, SA: Show<A>): String =
    "$label> MATCH (EOK): " + Nel.fromList(c).fold({ "<EMPTY>" }, { it.show().let { if (it.length > 40) it.substring(0, 40) + "<..>" else it } }) + "\n" +
            "$label> VALUE: " + SA.run { a.show() }

fun <S, EL, CHUNK, E> Stream<S, EL, CHUNK>.cErr(label: String, c: List<EL>, e: ParsecError<E, EL>, renderE: (E) -> Doc<Nothing>): String =
    "$label> MATCH (CERR): " + Nel.fromList(c).fold({ "<EMPTY>" }, { it.show().let { if (it.length > 40) it.substring(0, 40) + "<..>" else it } }) + "\n" +
            "$label> ERROR: " + e.errorDoc(this, renderE).layoutPretty(PageWidth.Unbounded).renderString()

fun <S, EL, CHUNK, E> Stream<S, EL, CHUNK>.eErr(label: String, c: List<EL>, e: ParsecError<E, EL>, renderE: (E) -> Doc<Nothing>): String =
    "$label> MATCH (EERR): " + Nel.fromList(c).fold({ "<EMPTY>" }, { it.show().let { if (it.length > 40) it.substring(0, 40) + "<..>" else it } }) + "\n" +
            "$label> ERROR: " + e.errorDoc(this, renderE).layoutPretty(PageWidth.Unbounded).renderString()

