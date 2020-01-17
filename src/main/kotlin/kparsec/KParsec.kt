package kparsec

import arrow.Kind
import arrow.Kind4
import arrow.core.*
import arrow.extension
import arrow.free.FreePartialOf
import arrow.free.fix
import arrow.free.runT
import arrow.syntax.collections.tail
import arrow.typeclasses.*
import pretty.Doc
import pretty.text

@JvmName("parseTestTrampoline")
fun <E, I, EL, CHUNK, A> KParsecT<E, I, EL, FreePartialOf<ForFunction0>, A>.parseTest(
    input: I,
    SI: Stream<I, EL, CHUNK>,
    SA: Show<A> = Show.any(),
    renderE: (E) -> Doc<Nothing> = { toString().text() }
): Unit = runParser("", input).fold({
    println(it.renderPretty(SI, renderE))
}, { SA.run { println(it.show()) } })

@JvmName("parseTestEval")
fun <E, I, EL, CHUNK, A> KParsecT<E, I, EL, ForEval, A>.parseTest(
    input: I,
    SI: Stream<I, EL, CHUNK>,
    SA: Show<A> = Show.any(),
    renderE: (E) -> Doc<Nothing> = { toString().text() }
): Unit = runParser("", input).fold({
    println(it.renderPretty(SI, renderE))
}, { SA.run { println(it.show()) } })

fun <E, I, EL, CHUNK, A> KParsecT<E, I, EL, ForId, A>.parseTest(
    input: I,
    SI: Stream<I, EL, CHUNK>,
    SA: Show<A> = Show.any(),
    renderE: (E) -> Doc<Nothing> = { toString().text() }
): Unit = runParser("", input).fold({
    println(it.renderPretty(SI, renderE))
}, { SA.run { println(it.show()) } })

@JvmName("runParserTrampoline")
fun <E, I, EL, A> KParsecT<E, I, EL, FreePartialOf<ForFunction0>, A>.runParser(
    name: String,
    input: I
): Either<ParseErrorBundle<E, I, EL>, A> =
    runParser(State.initialState(name, input)).b

@JvmName("runParserTrampoline")
fun <E, I, EL, A> KParsecT<E, I, EL, FreePartialOf<ForFunction0>, A>.runParser(
    state: State<I, E, EL>
): Tuple2<State<I, E, EL>, Either<ParseErrorBundle<E, I, EL>, A>> =
    runParserT(state).fix().runT()

@JvmName("runParserEval")
fun <E, I, EL, A> KParsecT<E, I, EL, ForEval, A>.runParser(
    name: String,
    input: I
): Either<ParseErrorBundle<E, I, EL>, A> =
    runParser(State.initialState(name, input)).b

@JvmName("runParserEval")
fun <E, I, EL, A> KParsecT<E, I, EL, ForEval, A>.runParser(
    state: State<I, E, EL>
): Tuple2<State<I, E, EL>, Either<ParseErrorBundle<E, I, EL>, A>> =
    runParserT(state).value()

fun <E, I, EL, A> KParsecT<E, I, EL, ForId, A>.runParser(
    name: String,
    input: I
): Either<ParseErrorBundle<E, I, EL>, A> =
    runParser(State.initialState(name, input)).b

fun <E, I, EL, A> KParsecT<E, I, EL, ForId, A>.runParser(
    state: State<I, E, EL>
): Tuple2<State<I, E, EL>, Either<ParseErrorBundle<E, I, EL>, A>> =
    runParserT(state).value()

fun <E, I, EL, M, A> KParsecT<E, I, EL, M, A>.runParserT(
    name: String,
    input: I
): Kind<M, Either<ParseErrorBundle<E, I, EL>, A>> =
    MM.run { runParserT(State.initialState(name, input)).map { it.b } }

fun <E, I, EL, M, A> KParsecT<E, I, EL, M, A>.runParserT(
    state: State<I, E, EL>
): Kind<M, Tuple2<State<I, E, EL>, Either<ParseErrorBundle<E, I, EL>, A>>> =
    MM.fx.monad {
        val reply = runParsecT(state).bind()

        reply.result.fold({ e ->
            reply.state toT Nel(e, reply.state.parseErrors).toBundle(reply.state).left()
        }, { a ->
            Nel.fromList(reply.state.parseErrors).fold({
                reply.state toT a.right()
            }, { xs -> reply.state toT xs.toBundle(reply.state).left() })
        })
    }

data class Hints<T>(val hints: List<Set<ErrorItem<T>>>) {
    operator fun plus(other: Hints<T>): Hints<T> = Hints(hints + other.hints)

    companion object {
        fun <T> empty(): Hints<T> = Hints(emptyList())
    }

    fun refreshHints(l: Option<ErrorItem<T>>): Hints<T> =
        if (hints.isEmpty()) this
        else l.fold({ Hints(hints.tail()) }, { Hints(listOf(setOf(it)) + hints.tail()) })
}

sealed class ParserState<out E, EL, out A> {
    data class ConsumedOk<EL, A>(val a: A, val hints: Hints<EL>) : ParserState<Nothing, EL, A>()
    data class ConsumedError<E, EL>(val e: ParsecError<E, EL>) : ParserState<E, EL, Nothing>()
    data class EmptyOk<EL, A>(val a: A, val hints: Hints<EL>) : ParserState<Nothing, EL, A>()
    data class EmptyError<E, EL>(val e: ParsecError<E, EL>) : ParserState<E, EL, Nothing>()

    fun <B> map(f: (A) -> B): ParserState<E, EL, B> = when (this) {
        is ConsumedOk -> ConsumedOk(f(a), hints)
        is EmptyOk -> EmptyOk(f(a), hints)
        else -> this as ParserState<E, EL, B>
    }
}

class ForKParsecT private constructor()
typealias KParsecTOf<E, I, EL, M, A> = Kind<KParsecTPartialOf<E, I, EL, M>, A>
typealias KParsecTPartialOf<E, I, EL, M> = Kind4<ForKParsecT, E, I, EL, M>

@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
inline fun <E, I, EL, M, A> KParsecTOf<E, I, EL, M, A>.fix(): KParsecT<E, I, EL, M, A> =
    this as KParsecT<E, I, EL, M, A>

data class KParsecT<E, I, EL, M, A>(
    val MM: Monad<M>,
    val pFun: (State<I, E, EL>) -> Kind<M, Tuple2<ParserState<E, EL, A>, State<I, E, EL>>>
) : KParsecTOf<E, I, EL, M, A> {

    fun <B> map(f: (A) -> B): KParsecT<E, I, EL, M, B> =
        KParsecT(MM, AndThen(pFun).andThen { MM.run { it.map { (s, i) -> s.map(f) toT i } } })

    fun <B> ap(ff: KParsecT<E, I, EL, M, (A) -> B>): KParsecT<E, I, EL, M, B> = lazyAp { ff }

    fun <B> lazyAp(ff: () -> KParsecT<E, I, EL, M, (A) -> B>): KParsecT<E, I, EL, M, B> =
        KParsecT(MM, AndThen(pFun).andThen {
            MM.run {
                it.flatMap { (a, i) ->
                    when (a) {
                        is ParserState.ConsumedOk -> ff().pFun(i).map { (ffa, s) ->
                            when (ffa) {
                                is ParserState.ConsumedOk -> ParserState.ConsumedOk(ffa.a(a.a), ffa.hints)
                                is ParserState.EmptyOk -> ParserState.ConsumedOk(ffa.a(a.a), a.hints + ffa.hints)
                                is ParserState.EmptyError -> ParserState.EmptyError(ffa.e.withHints(a.hints))
                                else -> (ffa as ParserState<E, EL, B>)
                            } toT s
                        }
                        is ParserState.EmptyOk -> ff().pFun(i).map { (ffa, s) ->
                            when (ffa) {
                                is ParserState.ConsumedOk -> ParserState.ConsumedOk(ffa.a(a.a), ffa.hints)
                                is ParserState.EmptyOk -> ParserState.EmptyOk(ffa.a(a.a), a.hints + ffa.hints)
                                is ParserState.EmptyError -> ParserState.EmptyError(ffa.e.withHints(a.hints))
                                else -> (ffa as ParserState<E, EL, B>)
                            } toT s
                        }
                        else -> MM.just((a as ParserState<E, EL, B>) toT i)
                    }
                }
            }
        })

    fun <B> flatMap(f: (A) -> KParsecT<E, I, EL, M, B>): KParsecT<E, I, EL, M, B> =
        KParsecT(MM, AndThen(pFun).andThen {
            MM.run {
                it.flatMap { (pa, s) ->
                    when (pa) {
                        is ParserState.ConsumedOk -> f(pa.a).pFun(s).map { (pa2, s2) ->
                            when (pa2) {
                                is ParserState.EmptyOk -> ParserState.EmptyOk(pa2.a, pa.hints + pa2.hints)
                                is ParserState.EmptyError -> ParserState.EmptyError(pa2.e.withHints(pa.hints))
                                else -> pa2
                            } toT s2
                        }
                        is ParserState.EmptyOk -> f(pa.a).pFun(s).map { (pa2, s2) ->
                            when (pa2) {
                                is ParserState.EmptyOk -> ParserState.EmptyOk(pa2.a, pa.hints + pa2.hints)
                                is ParserState.EmptyError -> ParserState.EmptyError(pa2.e.withHints(pa.hints))
                                else -> pa2
                            } toT s2
                        }
                        else -> MM.just((pa as ParserState<E, EL, B>) toT s)
                    }
                }
            }
        })

    fun orElse(other: KParsecT<E, I, EL, M, A>): KParsecT<E, I, EL, M, A> =
        KParsecT(MM, AndThen {
            MM.run {
                pFun(it).flatMap { (a, i) ->
                    when (a) {
                        is ParserState.ConsumedOk,
                        is ParserState.EmptyOk -> MM.just(a toT i)
                        is ParserState.EmptyError -> other.pFun(it).map { (pa, s) ->
                            when (pa) {
                                is ParserState.ConsumedError -> ParserState.ConsumedError(a.e + pa.e) toT i.longestMatch(
                                    s
                                )
                                is ParserState.EmptyError -> ParserState.EmptyError(a.e + pa.e) toT i.longestMatch(
                                    s
                                )
                                is ParserState.EmptyOk -> ParserState.EmptyOk(
                                    pa.a,
                                    a.e.toHints(s.offset) + pa.hints
                                ) toT s
                                is ParserState.ConsumedOk -> pa toT s
                            }
                        }
                        is ParserState.ConsumedError -> MM.just(a toT i)
                    }
                }
            }
        })

    companion object {
        fun <E, I, EL, CHUNK, M> monadParsec(
            SI: Stream<I, EL, CHUNK>,
            MM: Monad<M>
        ): MonadParsec<E, I, EL, CHUNK, KParsecTPartialOf<E, I, EL, M>> =
            object : KParsecTMonadParsec<E, I, EL, CHUNK, M> {
                override fun MM(): Monad<M> = MM
                override fun SI(): Stream<I, EL, CHUNK> = SI
            }
    }
}

data class Reply<E, I, EL, A>(
    val state: State<I, E, EL>,
    val hasConsumed: Boolean,
    val result: Either<ParsecError<E, EL>, A>
)

fun <E, I, EL, M, A> KParsecT<E, I, EL, M, A>.runParsecT(name: String, s: I): Kind<M, Reply<E, I, EL, A>> =
    runParsecT(State.initialState(name, s))

fun <E, I, EL, M, A> KParsecT<E, I, EL, M, A>.runParsecT(s: State<I, E, EL>): Kind<M, Reply<E, I, EL, A>> = MM.run {
    pFun(s).map { (pa, s) ->
        when (pa) {
            is ParserState.ConsumedOk -> Reply(s, true, pa.a.right())
            is ParserState.EmptyOk -> Reply(s, false, pa.a.right())
            is ParserState.ConsumedError -> Reply<E, I, EL, A>(s, true, pa.e.left())
            is ParserState.EmptyError -> Reply<E, I, EL, A>(s, false, pa.e.left())
        }
    }
}

@extension
interface KParsecTMonadParsec<E, I, EL, CHUNK, M> : MonadParsec<E, I, EL, CHUNK, KParsecTPartialOf<E, I, EL, M>> {
    override fun SI(): Stream<I, EL, CHUNK>
    fun MM(): Monad<M>

    override fun <A> empty(): Kind<KParsecTPartialOf<E, I, EL, M>, A> = KParsecT(MM(), AndThen {
        MM().just(ParserState.EmptyError(ParsecError.Trivial<EL>(it.offset, None, emptySet())) toT it)
    })

    override fun <A> Kind<KParsecTPartialOf<E, I, EL, M>, A>.some(): Kind<KParsecTPartialOf<E, I, EL, M>, SequenceK<A>> =
        fix().lazyAp { many().fix().map { xs -> { x: A -> (sequenceOf(x) + xs).k() } } }

    override fun <A> Kind<KParsecTPartialOf<E, I, EL, M>, A>.orElse(b: Kind<KParsecTPartialOf<E, I, EL, M>, A>): Kind<KParsecTPartialOf<E, I, EL, M>, A> =
        fix().orElse(b.fix())

    override fun <A, B> Kind<KParsecTPartialOf<E, I, EL, M>, A>.flatMap(f: (A) -> Kind<KParsecTPartialOf<E, I, EL, M>, B>): Kind<KParsecTPartialOf<E, I, EL, M>, B> =
        fix().flatMap(f.andThen { it.fix() })

    override fun <A> just(a: A): Kind<KParsecTPartialOf<E, I, EL, M>, A> = KParsecT(MM(), AndThen {
        MM().just(ParserState.EmptyOk(a, Hints.empty<EL>()) toT it)
    })

    override fun <A, B> tailRecM(
        a: A,
        f: (A) -> Kind<KParsecTPartialOf<E, I, EL, M>, Either<A, B>>
    ): Kind<KParsecTPartialOf<E, I, EL, M>, B> = KParsecT(MM(), AndThen {
        MM().run {
            f(a).fix().pFun(it).flatMap { (pa, s) ->
                when (pa) {
                    is ParserState.ConsumedOk -> pa.a.fold({ tailRecM(it, f).fix().pFun(s) }, {
                        just(ParserState.ConsumedOk(it, pa.hints) toT s)
                    })
                    is ParserState.EmptyOk -> pa.a.fold({ tailRecM(it, f).fix().pFun(s) }, {
                        just(ParserState.ConsumedOk(it, pa.hints) toT s)
                    })
                    else -> just((pa as ParserState<E, EL, B>) toT s)
                }
            }
        }
    })

    override fun <A> Kind<KParsecTPartialOf<E, I, EL, M>, A>.handleErrorWith(f: (ParsecError<E, EL>) -> Kind<KParsecTPartialOf<E, I, EL, M>, A>): Kind<KParsecTPartialOf<E, I, EL, M>, A> =
        fix().withRecovery(f)

    override fun <A> raiseError(e: ParsecError<E, EL>): Kind<KParsecTPartialOf<E, I, EL, M>, A> =
        parseError(e)

    override fun <A> parseError(e: ParsecError<E, EL>): Kind<KParsecTPartialOf<E, I, EL, M>, A> =
        KParsecT(MM()) { MM().just(ParserState.EmptyError(e) toT it) }

    override fun <A> Kind<KParsecTPartialOf<E, I, EL, M>, A>.label(str: String): Kind<KParsecTPartialOf<E, I, EL, M>, A> =
        KParsecT(MM(), AndThen(fix().pFun).andThen {
            MM().run {
                it.map { (pa, s) ->
                    val l = if (str.isEmpty()) None
                    else ErrorItem.Label(str).some()

                    when (pa) {
                        is ParserState.ConsumedOk -> ParserState.ConsumedOk(
                            pa.a,
                            l.fold({ pa.hints }, { pa.hints.refreshHints(None) })
                        )
                        is ParserState.EmptyOk -> ParserState.EmptyOk(pa.a, pa.hints.refreshHints(l))
                        is ParserState.EmptyError -> ParserState.EmptyError(
                            when (val err = pa.e) {
                                is ParsecError.Trivial -> ParsecError.Trivial(
                                    err.offset,
                                    err.unexpectedTokens,
                                    l.fold({ emptySet<ErrorItem<EL>>() }, { setOf(it) })
                                )
                                else -> err
                            }
                        )
                        is ParserState.ConsumedError -> pa
                    } toT s
                }
            }
        })

    override fun eof(): Kind<KParsecTPartialOf<E, I, EL, M>, Unit> = KParsecT(MM(), AndThen { s ->
        SI().run {
            MM().just(
                s.input.takeOne().fold({
                    ParserState.EmptyOk(Unit, Hints.empty<EL>()) toT s
                }, { (x, _) ->
                    ParserState.EmptyError<E, EL>(
                        ParsecError.Trivial(
                            s.offset, ErrorItem.Tokens(Nel(x)).some(), setOf(ErrorItem.EndOfInput)
                        )
                    ) toT s
                })
            )
        }
    })

    override fun getParserState(): Kind<KParsecTPartialOf<E, I, EL, M>, State<I, E, EL>> = KParsecT(MM(), AndThen {
        MM().just(
            ParserState.EmptyOk(it, Hints.empty<EL>()) toT it
        )
    })

    override fun <A> Kind<KParsecTPartialOf<E, I, EL, M>, A>.lookAhead(): Kind<KParsecTPartialOf<E, I, EL, M>, A> =
        KParsecT(MM(), AndThen { s ->
            MM().run {
                fix().pFun(s).map { (pa, _) ->
                    when (pa) {
                        is ParserState.ConsumedOk -> ParserState.EmptyOk(pa.a, Hints.empty<EL>()) toT s
                        is ParserState.EmptyOk -> ParserState.EmptyOk(pa.a, Hints.empty<EL>()) toT s
                        else -> pa toT s
                    }
                }
            }
        })

    override fun <A> Kind<KParsecTPartialOf<E, I, EL, M>, A>.notFollowedBy(): Kind<KParsecTPartialOf<E, I, EL, M>, Unit> =
        KParsecT(MM(), AndThen { s ->
            MM().run {
                fix().pFun(s).map { (pa, _) ->
                    val what = {
                        SI().run {
                            s.input.takeOne().fold({ ErrorItem.EndOfInput }, { ErrorItem.Tokens(Nel(it.a)) })
                        }
                    }
                    val tErr = { ParsecError.Trivial<EL>(s.offset, what().some(), emptySet()) }
                    when (pa) {
                        is ParserState.ConsumedOk -> ParserState.EmptyError(tErr()) toT s
                        is ParserState.EmptyOk -> ParserState.EmptyError(tErr()) toT s
                        is ParserState.ConsumedError -> ParserState.EmptyOk(Unit, Hints.empty<EL>()) toT s
                        is ParserState.EmptyError -> ParserState.EmptyOk(Unit, Hints.empty<EL>()) toT s
                    }
                }
            }
        })

    override fun <A> Kind<KParsecTPartialOf<E, I, EL, M>, A>.observing(): Kind<KParsecTPartialOf<E, I, EL, M>, Either<ParsecError<E, EL>, A>> =
        KParsecT(MM(), AndThen(fix().pFun).andThen {
            MM().run {
                it.map { (pa, s) ->
                    when (pa) {
                        is ParserState.ConsumedOk -> ParserState.ConsumedOk(
                            pa.a.right(),
                            Hints.empty<EL>()
                        )
                        is ParserState.ConsumedError -> ParserState.ConsumedOk(
                            pa.e.left(),
                            Hints.empty<EL>()
                        )
                        is ParserState.EmptyOk -> ParserState.EmptyOk(
                            pa.a.right(),
                            Hints.empty<EL>()
                        )
                        is ParserState.EmptyError -> ParserState.EmptyOk(
                            pa.e.left(),
                            pa.e.toHints(s.offset)
                        )
                    } toT s
                }
            }
        })

    override fun take(label: Option<String>, n: Int): Kind<KParsecTPartialOf<E, I, EL, M>, CHUNK> =
        KParsecT(MM(), AndThen {
            SI().run {
                it.input.take(n).fold({
                    MM().just(ParserState.EmptyError<E, EL>(ParsecError.Trivial(
                        it.offset,
                        ErrorItem.EndOfInput.some(),
                        label.filter { it.isNotEmpty() }.fold({ emptySet<ErrorItem<EL>>() }, { label ->
                            setOf(ErrorItem.Label(label))
                        })
                    )
                    ) toT it
                    )
                }, { (c, i) ->
                    if (c.size() != n) MM().just(ParserState.EmptyError<E, EL>(ParsecError.Trivial(
                        it.offset + c.size(),
                        ErrorItem.EndOfInput.some(),
                        label.filter { it.isNotEmpty() }.fold({ emptySet<ErrorItem<EL>>() }, { label ->
                            setOf(ErrorItem.Label(label))
                        })
                    )
                    ) toT it
                    )
                    else MM().just(ParserState.ConsumedOk(c, Hints.empty<EL>()) toT State(i, it.offset + n, it.posState, it.parseErrors))
                })
            }
        })

    override fun takeAtLeastOneWhile(
        label: Option<String>,
        matcher: (EL) -> Boolean
    ): Kind<KParsecTPartialOf<E, I, EL, M>, CHUNK> = KParsecT(MM(), AndThen {
        SI().run {
            it.input.takeWhile(matcher).let { (chunk, rem) ->
                if (chunk.isEmpty()) MM().just(
                    ParserState.EmptyError<E, EL>(
                        ParsecError.Trivial(
                            it.offset,
                            it.input.takeOne().fold({ ErrorItem.EndOfInput },
                                { (x, _) -> ErrorItem.Tokens(Nel(x)) }).some(),
                            label.filter { it.isNotEmpty() }.fold(
                                { emptySet<ErrorItem<EL>>() },
                                { l -> setOf(ErrorItem.Label(l)) })
                        )
                    ) toT it
                )
                else MM().just(
                    ParserState.ConsumedOk(
                        chunk, Hints(
                            label.filter { it.isNotEmpty() }.fold(
                                { emptyList<Set<ErrorItem<EL>>>() },
                                { l -> listOf(setOf(ErrorItem.Label(l))) })
                        )
                    ) toT State(rem, it.offset + chunk.size(), it.posState, it.parseErrors)
                )
            }
        }
    })

    override fun takeWhile(
        label: Option<String>,
        matcher: (EL) -> Boolean
    ): Kind<KParsecTPartialOf<E, I, EL, M>, CHUNK> = KParsecT(MM(), AndThen {
        SI().run {
            it.input.takeWhile(matcher).let { (chunk, rem) ->
                if (chunk.isEmpty()) MM().just(
                    ParserState.EmptyOk(chunk, Hints.empty<EL>()) toT State(
                        rem, it.offset + chunk.size(), it.posState, it.parseErrors
                    )
                )
                else MM().just(
                    ParserState.ConsumedOk(chunk, Hints.empty<EL>()) toT State(
                        rem, it.offset + chunk.size(), it.posState, it.parseErrors
                    )
                )
            }
        }
    })

    override fun <A> token(
        expected: Set<ErrorItem<EL>>,
        matcher: (EL) -> Option<A>
    ): Kind<KParsecTPartialOf<E, I, EL, M>, A> = KParsecT(MM(), AndThen {
        SI().run {
            it.input.takeOne().fold({
                MM().just(
                    ParserState.EmptyError<E, EL>(
                        ParsecError.Trivial(
                            it.offset,
                            ErrorItem.EndOfInput.some(),
                            expected
                        )
                    ) toT it
                )
            }, { (el, rem) ->
                matcher(el).fold({
                    MM().just(
                        ParserState.EmptyError<E, EL>(
                            ParsecError.Trivial(
                                it.offset,
                                ErrorItem.Tokens(Nel(el)).some(),
                                expected
                            )
                        ) toT it
                    )
                }, { a ->
                    MM().just(ParserState.ConsumedOk<EL, A>(a, Hints.empty()) toT State(rem, it.offset + 1, it.posState, it.parseErrors))
                })
            })
        }
    })

    override fun tokens(chunk: CHUNK): Kind<KParsecTPartialOf<E, I, EL, M>, CHUNK> = KParsecT(MM(), AndThen {
        SI().run {
            it.input.take(chunk.size()).fold({
                MM().just(
                    ParserState.EmptyError<E, EL>(
                        ParsecError.Trivial(
                            it.offset,
                            ErrorItem.EndOfInput.some(),
                            setOf(ErrorItem.Tokens(Nel.fromListUnsafe(chunk.toTokens())))
                        )
                    ) toT it
                )
            }, { (c, rem) ->
                if (c.size() != chunk.size() || SI().EQCHUNK().run { c.neqv(chunk) }) MM().just(
                    ParserState.EmptyError<E, EL>(
                        ParsecError.Trivial(
                            it.offset,
                            ErrorItem.Tokens(Nel.fromListUnsafe(c.toTokens())).some(),
                            setOf(ErrorItem.Tokens(Nel.fromListUnsafe(chunk.toTokens())))
                        )
                    ) toT it
                )
                else MM().just(
                    ParserState.ConsumedOk<EL, CHUNK>(c, Hints.empty()) toT State(
                        rem,
                        it.offset + c.size(),
                        it.posState, it.parseErrors
                    )
                )
            })
        }
    })

    override fun updateParserState(f: (State<I, E, EL>) -> State<I, E, EL>): Kind<KParsecTPartialOf<E, I, EL, M>, Unit> =
        KParsecT(MM(), AndThen { MM().just(ParserState.EmptyOk<EL, Unit>(Unit, Hints.empty()) toT f(it)) })

    override fun <A> Kind<KParsecTPartialOf<E, I, EL, M>, A>.tryP(): Kind<KParsecTPartialOf<E, I, EL, M>, A> =
        KParsecT(MM(), AndThen {
            MM().run {
                fix().pFun(it).map { (pa, s) ->
                    when (pa) {
                        is ParserState.EmptyError -> ParserState.EmptyError(pa.e) toT it
                        else -> pa toT s
                    }
                }
            }
        })

    override fun <A> Kind<KParsecTPartialOf<E, I, EL, M>, A>.withRecovery(h: (ParsecError<E, EL>) -> Kind<KParsecTPartialOf<E, I, EL, M>, A>): Kind<KParsecTPartialOf<E, I, EL, M>, A> =
        KParsecT(MM(), AndThen {
            MM().run {
                fix().pFun(it).flatMap { (pa, i) ->
                    when (pa) {
                        is ParserState.ConsumedError -> h(pa.e).fix().pFun(i).map { (pa2, s) ->
                            when (pa2) {
                                is ParserState.ConsumedOk -> ParserState.ConsumedOk(pa2.a, Hints.empty())
                                is ParserState.ConsumedError -> ParserState.ConsumedError(pa.e)
                                is ParserState.EmptyOk -> ParserState.EmptyOk(pa2.a, pa.e.toHints(s.offset))
                                is ParserState.EmptyError -> ParserState.EmptyError(pa.e)
                            } toT s
                        }
                        is ParserState.EmptyError -> h(pa.e).fix().pFun(i).map { (pa2, s) ->
                            when (pa2) {
                                is ParserState.ConsumedOk -> ParserState.ConsumedOk(pa2.a, pa.e.toHints(s.offset))
                                is ParserState.ConsumedError -> ParserState.ConsumedError(pa.e)
                                is ParserState.EmptyOk -> ParserState.EmptyOk(pa2.a, pa.e.toHints(s.offset))
                                is ParserState.EmptyError -> ParserState.EmptyError(pa.e)
                            } toT s
                        }
                        else -> just(pa toT i)
                    }
                }
            }
        })

    override fun getOffset(): Kind<KParsecTPartialOf<E, I, EL, M>, Int> =
        KParsecT(MM()) { MM().just(ParserState.EmptyOk<EL, Int>(it.offset, Hints.empty()) toT it) }
}

interface MonadParsec<E, I, EL, CHUNK, M> : MonadError<M, ParsecError<E, EL>>, Alternative<M> {
    fun SI(): Stream<I, EL, CHUNK>

    fun <A> fx(f: suspend MonadSyntax<M>.() -> A): Kind<M, A> = fx.monad(f)

    fun <A> parseError(e: ParsecError<E, EL>): Kind<M, A>

    fun <A> Kind<M, A>.label(str: String): Kind<M, A>

    fun <A> Kind<M, A>.hidden(): Kind<M, A> = label("")

    fun <A> Kind<M, A>.tryP(): Kind<M, A>

    fun <A> Kind<M, A>.lookAhead(): Kind<M, A>

    fun <A> Kind<M, A>.notFollowedBy(): Kind<M, Unit>

    fun <A> Kind<M, A>.withRecovery(h: (ParsecError<E, EL>) -> Kind<M, A>): Kind<M, A>

    fun <A> Kind<M, A>.observing(): Kind<M, Either<ParsecError<E, EL>, A>>

    fun eof(): Kind<M, Unit>

    fun <A> token(expected: Set<ErrorItem<EL>>, matcher: (EL) -> Option<A>): Kind<M, A>

    fun tokens(chunk: CHUNK): Kind<M, CHUNK>

    fun takeWhile(label: Option<String>, matcher: (EL) -> Boolean): Kind<M, CHUNK>

    fun takeAtLeastOneWhile(label: Option<String>, matcher: (EL) -> Boolean): Kind<M, CHUNK>

    fun take(label: Option<String>, n: Int): Kind<M, CHUNK>

    fun getParserState(): Kind<M, State<I, E, EL>>

    fun updateParserState(f: (State<I, E, EL>) -> State<I, E, EL>): Kind<M, Unit>

    fun getOffset(): Kind<M, Int> = getParserState().map { it.offset }

    fun setOffset(i: Int): Kind<M, Unit> = updateParserState { it.copy(offset = i) }

    fun getInput(): Kind<M, I> = getParserState().map { it.input }

    fun setInput(i: I): Kind<M, Unit> = updateParserState { it.copy(input = i) }

    fun getSourcePos(): Kind<M, SourcePos> = fx.monad {
        val st = getParserState().bind()
        val pst = SI().run { reachOffsetNoNewline(st.offset, st.posState) }
        updateParserState { st.copy(posState = pst) }.bind()
        pst.sourcePos
    }

    fun <A> Kind<M, A>.region(f: (ParsecError<E, EL>) -> ParsecError<E, EL>): Kind<M, A> = fx.monad {
        val deSoFar = getParserState().map { it.parseErrors }.bind()
        updateParserState { it.copy(parseErrors = emptyList()) }.bind()
        val r = this@region.observing().bind()
        updateParserState {
            it.copy(parseErrors = it.parseErrors.map(f) + deSoFar)
        }.bind()

        r.fold({ parseError<A>(it).bind() }, ::identity)
    }

    fun <A> Kind<M, A>.match(): Kind<M, Tuple2<CHUNK, A>> = fx.monad {
        val o = getOffset().bind()
        val i = getInput().bind()
        val r = this@match.bind()
        val o2 = getOffset().bind()

        SI().run { i.take(o2 - o).getOrElse { throw IllegalStateException("The impossible happened. Did you mess with the offset inside match?") }.a } toT r
    }
}

// parse error combinators
fun <E, I, EL, CHUNK, M, A> MonadParsec<E, I, EL, CHUNK, M>.failure(
    unexpected: Option<ErrorItem<EL>>,
    expected: Set<ErrorItem<EL>>
): Kind<M, A> = getOffset().flatMap { parseError<A>(ParsecError.Trivial(it, unexpected, expected)) }

fun <E, I, EL, CHUNK, M, A> MonadParsec<E, I, EL, CHUNK, M>.fancyFailure(
    errors: Set<E>
): Kind<M, A> = getOffset().flatMap { parseError<A>(ParsecError.Fancy(it, errors)) }

fun <E, I, EL, CHUNK, M, A> MonadParsec<E, I, EL, CHUNK, M>.unexpected(
    item: ErrorItem<EL>
): Kind<M, A> = failure(item.some(), emptySet())

fun <E, I, EL, CHUNK, M, A> MonadParsec<E, I, EL, CHUNK, M>.fancyFailure(
    error: E
): Kind<M, A> = fancyFailure(setOf(error))

fun <E, I, EL, CHUNK, M> MonadParsec<E, I, EL, CHUNK, M>.registerParseError(
    e: ParsecError<E, EL>
): Kind<M, Unit> = updateParserState { it.copy(parseErrors = listOf(e) + it.parseErrors) }

fun <E, I, EL, CHUNK, M> MonadParsec<E, I, EL, CHUNK, M>.registerFailure(
    unexpected: Option<ErrorItem<EL>>,
    expected: Set<ErrorItem<EL>>
): Kind<M, Unit> = getOffset().flatMap { registerParseError(ParsecError.Trivial(it, unexpected, expected)) }

fun <E, I, EL, CHUNK, M> MonadParsec<E, I, EL, CHUNK, M>.registerFancyFailure(
    errors: Set<E>
): Kind<M, Unit> = getOffset().flatMap { registerParseError(ParsecError.Fancy(it, errors)) }

// combinators
fun <E, I, EL, CHUNK, M> MonadParsec<E, I, EL, CHUNK, M>.single(el: EL): Kind<M, EL> =
    token(setOf(ErrorItem.Tokens(Nel(el)))) { it.some().filter { SI().EQEL().run { el.eqv(it) } } }

fun <E, I, EL, CHUNK, M> MonadParsec<E, I, EL, CHUNK, M>.satisfy(p: (EL) -> Boolean): Kind<M, EL> =
    token(emptySet()) { it.some().filter(p) }

fun <E, I, EL, CHUNK, M> MonadParsec<E, I, EL, CHUNK, M>.anySingle(): Kind<M, EL> = satisfy { true }

fun <E, I, EL, CHUNK, M> MonadParsec<E, I, EL, CHUNK, M>.anySingleBut(el: EL): Kind<M, EL> =
    satisfy { SI().EQEL().run { el.neqv(it) } }

fun <F, E, I, EL, CHUNK, M> MonadParsec<E, I, EL, CHUNK, M>.oneOf(cs: Kind<F, EL>, FF: Foldable<F>): Kind<M, EL> =
    satisfy { c -> FF.run { cs.exists { SI().EQEL().run { c.eqv(it) } } } }

fun <F, E, I, EL, CHUNK, M> MonadParsec<E, I, EL, CHUNK, M>.noneOf(cs: Kind<F, EL>, FF: Foldable<F>): Kind<M, EL> =
    satisfy { c -> FF.run { cs.forAll { SI().EQEL().run { c.neqv(it) } } } }

fun <E, I, EL, CHUNK, M> MonadParsec<E, I, EL, CHUNK, M>.chunk(chunk: CHUNK): Kind<M, CHUNK> =
    tokens(chunk)

fun <E, I, EL, CHUNK, M> MonadParsec<E, I, EL, CHUNK, M>.takeRemaining(): Kind<M, CHUNK> =
    takeWhile(None) { true }

fun <E, I, EL, CHUNK, M> MonadParsec<E, I, EL, CHUNK, M>.atEnd(): Kind<M, Boolean> =
    eof().hidden().mapConst(true).optional().map { it.fold({ false }, ::identity) }

