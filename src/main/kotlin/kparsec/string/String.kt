package kparsec.string

import arrow.Kind
import arrow.core.*
import arrow.core.extensions.eq
import arrow.core.extensions.list.foldable.foldLeft
import arrow.typeclasses.Eq
import kparsec.*

// combinators
fun <E, I, CHUNK, M> MonadParsec<E, I, Char, CHUNK, M>.newline(): Kind<M, Char> =
    char('\n').label("newline")

fun <E, I, CHUNK, M> MonadParsec<E, I, Char, CHUNK, M>.crlf(): Kind<M, CHUNK> =
    string(SI().run { listOf('\r', '\n').toChunk() })

fun <E, I, CHUNK, M> MonadParsec<E, I, Char, CHUNK, M>.eol(): Kind<M, CHUNK> =
    newline().map { SI().run { it.toChunk() } }.orElse(crlf())
        .label("end of line")

fun <E, I, CHUNK, M> MonadParsec<E, I, Char, CHUNK, M>.tab(): Kind<M, Char> =
    char('\t').label("tab")

fun <E, I, CHUNK, M> MonadParsec<E, I, Char, CHUNK, M>.space(): Kind<M, Unit> =
    takeWhile("white space".some()) { it.isWhitespace() }.unit()


fun <E, I, CHUNK, M> MonadParsec<E, I, Char, CHUNK, M>.atLeastOneSpace(): Kind<M, Unit> =
    takeAtLeastOneWhile("white space".some()) { it.isWhitespace() }.unit()

fun <E, I, CHUNK, M> MonadParsec<E, I, Char, CHUNK, M>.controlChar(): Kind<M, Char> =
    satisfy { it.isISOControl() }.label("control character")

fun <E, I, CHUNK, M> MonadParsec<E, I, Char, CHUNK, M>.spaceChar(): Kind<M, Char> =
    satisfy { it.isWhitespace() }.label("white space")

fun <E, I, CHUNK, M> MonadParsec<E, I, Char, CHUNK, M>.upperChar(): Kind<M, Char> =
    satisfy { it.isUpperCase() }.label("uppercase letter")

fun <E, I, CHUNK, M> MonadParsec<E, I, Char, CHUNK, M>.lowerChar(): Kind<M, Char> =
    satisfy { it.isLowerCase() }.label("lowercase letter")

fun <E, I, CHUNK, M> MonadParsec<E, I, Char, CHUNK, M>.letterChar(): Kind<M, Char> =
    satisfy { it.isLetter() }.label("letter")

fun <E, I, CHUNK, M> MonadParsec<E, I, Char, CHUNK, M>.digitChar(): Kind<M, Char> =
    satisfy { it.isDigit() }.label("digit")

fun <E, I, CHUNK, M> MonadParsec<E, I, Char, CHUNK, M>.char(c: Char): Kind<M, Char> =
    single(c)

fun <E, I, EL, CHUNK, M> MonadParsec<E, I, EL, CHUNK, M>.string(str: CHUNK): Kind<M, CHUNK> =
    chunk(str)

// TODO kotlin has inbuilt support for these, so if we fix CHUNK to String we get this for free
fun <E, I, CHUNK, M> MonadParsec<E, I, Char, CHUNK, M>.decimal(): Kind<M, Long> =
    takeAtLeastOneWhile("decimal digit".some()) { it in '0'..'9' }.map { chunk ->
        SI().run {
            chunk.toTokens().foldLeft(0L) { acc, v ->
                acc * 10L + v.toString().toInt(10)
            }
        }
    }

fun <E, I, CHUNK, M> MonadParsec<E, I, Char, CHUNK, M>.binary(): Kind<M, Long> =
    takeAtLeastOneWhile("binary digit".some()) { it == '0' || it == '1' }.map { chunk ->
        SI().run {
            chunk.toTokens().foldLeft(0L) { acc, v ->
                acc * 2L + v.toString().toInt(2)
            }
        }
    }


fun <E, I, CHUNK, M> MonadParsec<E, I, Char, CHUNK, M>.octal(): Kind<M, Long> =
    takeAtLeastOneWhile("octal digit".some()) { it in '0'..'7' }.map { chunk ->
        SI().run {
            chunk.toTokens().foldLeft(0L) { acc, v ->
                acc * 2L + v.toString().toInt(8)
            }
        }
    }

fun <E, I, CHUNK, M> MonadParsec<E, I, Char, CHUNK, M>.hexadecimal(): Kind<M, Long> =
    takeAtLeastOneWhile("hexadecimal digit".some()) { it.isHexDigit() }.map { chunk ->
        SI().run {
            chunk.toTokens().foldLeft(0L) { acc, v ->
                acc * 16L + v.toString().toInt(16)
            }
        }
    }

fun Char.isHexDigit(): Boolean = this in ('0'..'9') || this in ('A'..'F') || this in ('a'..'f')

fun <E, I, CHUNK, M> MonadParsec<E, I, Char, CHUNK, M>.signedLong(p: Kind<M, Long>): Kind<M, Long> =
    (char('+').map { { l: Long -> l } }.orElse(char('-').map { { l: Long -> l * (-1) } }))
        .optional()
        .flatMap { optF ->
            p.map { optF.fold({ { l: Long -> l } }, ::identity)(it) }
        }

fun <E, I, CHUNK, M> MonadParsec<E, I, Char, CHUNK, M>.signedDouble(p: Kind<M, Double>): Kind<M, Double> =
    (char('+').map { { l: Double -> l } }.orElse(char('-').map { { l: Double -> l * (-1) } }))
        .optional()
        .ap(p.map { x -> { optF: Option<(Double) ->Double> -> optF.fold({ { l: Double -> l } }, ::identity)(x) } })

fun <E, I, CHUNK, M> MonadParsec<E, I, Char, CHUNK, M>.double(): Kind<M, Double> =
    // TODO this is really ugly ^^
    decimal().apTap(char('.')).ap(decimal().map { snd -> { fst: Long -> "$fst.$snd".toDouble() } })
