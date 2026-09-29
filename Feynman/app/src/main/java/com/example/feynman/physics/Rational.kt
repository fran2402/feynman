package com.example.feynman.physics

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext

/** An exact fraction num/den, always in lowest terms with den > 0. */
class Rational private constructor(val num: BigInteger, val den: BigInteger) : Comparable<Rational> {

    companion object {
        val ZERO = Rational(BigInteger.ZERO, BigInteger.ONE)
        val ONE = Rational(BigInteger.ONE, BigInteger.ONE)

        fun of(num: BigInteger, den: BigInteger = BigInteger.ONE): Rational {
            require(den.signum() != 0) { "Division by zero" }
            if (num.signum() == 0) return ZERO
            val g = num.gcd(den)
            var n = num / g
            var d = den / g
            if (d.signum() < 0) { n = -n; d = -d }
            return Rational(n, d)
        }

        fun of(n: Long, d: Long = 1) = of(BigInteger.valueOf(n), BigInteger.valueOf(d))

        /** Parses "12.5", ".5" or "7" exactly. */
        fun parseDecimal(text: String): Rational {
            val bd = BigDecimal(if (text.startsWith(".")) "0$text" else text.removeSuffix("."))
            return if (bd.scale() > 0) of(bd.unscaledValue(), BigInteger.TEN.pow(bd.scale()))
            else of(bd.toBigIntegerExact())
        }

        /** Exact value of a double (for constants typed as decimals). */
        fun ofDecimalString(text: String) = BigDecimal(text).let {
            if (it.scale() > 0) of(it.unscaledValue(), BigInteger.TEN.pow(it.scale()))
            else of(it.unscaledValue() * BigInteger.TEN.pow(-it.scale()))
        }
    }

    val isInteger get() = den == BigInteger.ONE
    val signum get() = num.signum()

    operator fun plus(o: Rational) = of(num * o.den + o.num * den, den * o.den)
    operator fun minus(o: Rational) = of(num * o.den - o.num * den, den * o.den)
    operator fun times(o: Rational) = of(num * o.num, den * o.den)
    operator fun div(o: Rational) = of(num * o.den, den * o.num)
    operator fun unaryMinus() = of(-num, den)
    fun abs() = if (signum < 0) -this else this
    fun reciprocal() = of(den, num)

    fun pow(n: Int): Rational = when {
        n >= 0 -> of(num.pow(n), den.pow(n))
        else -> of(den.pow(-n), num.pow(-n))
    }

    fun floor(): BigInteger {
        val (q, r) = num.divideAndRemainder(den)
        return if (r.signum() < 0) q - BigInteger.ONE else q
    }

    fun ceil(): BigInteger = -((-this).floor())

    override fun compareTo(other: Rational) = (num * other.den).compareTo(other.num * den)
    override fun equals(other: Any?) = other is Rational && num == other.num && den == other.den
    override fun hashCode() = num.hashCode() * 31 + den.hashCode()

    fun toDouble(): Double {
        val d = num.toDouble() / den.toDouble()
        if (d.isFinite() && d != 0.0) return d
        return BigDecimal(num).divide(BigDecimal(den), MathContext.DECIMAL64).toDouble()
    }

    fun toBigDecimal(mc: MathContext): BigDecimal = BigDecimal(num).divide(BigDecimal(den), mc)

    override fun toString() = if (isInteger) num.toString() else "$num/$den"
}

