package com.example.smartalarm

import kotlin.random.Random

enum class TaskType(val title: String) {
    MATH("Математика"),
    CAESAR("Шифр Цезаря"),
    SEQUENCE("Логическая последовательность"),
    CAPTCHA("Ввод текста (капча)"),
    EQUATION("Уравнение");

    companion object {
        fun fromName(n: String): TaskType? = values().firstOrNull { it.name == n }
    }
}

data class Task(
    val type: TaskType,
    val prompt: String,
    val answer: String,
    val captcha: String? = null,
    val difficulty: Int = 0,
    val caseSensitive: Boolean = false
) {
    fun check(input: String): Boolean = input.trim().equals(answer, ignoreCase = !caseSensitive)
}

object TaskGenerator {
    private val words = listOf(
        "apple", "river", "stone", "light", "cloud", "bridge", "garden", "window", "planet", "silver",
        "morning", "pattern", "machine", "mountain", "journey", "thunder", "keyboard", "universe",
        "calendar", "elephant"
    )

    fun randomTypes(count: Int): MutableList<String> =
        MutableList(count) { TaskType.values().random().name }

    fun generate(typeName: String, difficulty: Int): Task {
        val d = difficulty.coerceIn(0, 10)
        val type = TaskType.fromName(typeName) ?: TaskType.MATH
        return when (type) {
            TaskType.MATH -> math(d)
            TaskType.CAESAR -> caesar(d)
            TaskType.SEQUENCE -> sequence(d)
            TaskType.CAPTCHA -> captcha(d)
            TaskType.EQUATION -> equation(d)
        }
    }

    private fun math(d: Int): Task {
        val r = Random
        val terms = 2 + d / 4
        val maxN = 10 + d * 10
        val sb = StringBuilder()
        var total = 0
        for (i in 0 until terms) {
            val product = d >= 3 && r.nextInt(100) < 30 + d * 5
            val value: Int
            val text: String
            if (product) {
                val a = r.nextInt(2, 6 + d)
                val b = r.nextInt(2, 6 + d)
                value = a * b
                text = "$a × $b"
            } else {
                value = r.nextInt(1, maxN + 1)
                text = "$value"
            }
            if (i == 0) {
                sb.append(text)
                total = value
            } else {
                var minus = r.nextBoolean()
                if (d < 3 && minus && total < value) minus = false
                sb.append(if (minus) " − " else " + ").append(text)
                total += if (minus) -value else value
            }
        }
        return Task(TaskType.MATH, "Вычислите:\n$sb = ?", total.toString(), difficulty = d)
    }

    private fun signed(n: Int) = if (n < 0) "− ${-n}" else "+ $n"

    private fun equation(d: Int): Task {
        val r = Random
        val text: String
        val x: Int
        when {
            d <= 2 -> {
                x = r.nextInt(1, 15 + d * 5)
                val a = r.nextInt(1, 20)
                text = "x + $a = ${x + a}"
            }
            d <= 5 -> {
                x = r.nextInt(2, 13)
                val a = r.nextInt(2, 10)
                val b = r.nextInt(1, 30)
                text = "${a}x + $b = ${a * x + b}"
            }
            d <= 8 -> {
                x = r.nextInt(2, 16)
                val c = r.nextInt(2, 6)
                val a = c + r.nextInt(1, 6)
                val b = r.nextInt(1, 30)
                val e = a * x + b - c * x
                text = "${a}x + $b = ${c}x + $e"
            }
            else -> {
                x = r.nextInt(-12, 13)
                val a = r.nextInt(2, 8)
                val b = r.nextInt(-9, 10)
                val c = r.nextInt(1, 20)
                val e = a * (x + b) - c
                text = "$a(x ${signed(b)}) − $c = $e"
            }
        }
        return Task(TaskType.EQUATION, "Найдите x (целое число):\n$text", x.toString(), difficulty = d)
    }

    private fun caesar(d: Int): Task {
        val r = Random
        val shift = when {
            d <= 2 -> r.nextInt(1, 3)
            d <= 5 -> r.nextInt(3, 7)
            d <= 8 -> r.nextInt(7, 14)
            else -> r.nextInt(14, 26)
        }
        val minLen = (3 + d / 2).coerceAtMost(8)
        val pool = words.filter { it.length >= minLen }.ifEmpty { words }
        val plain = if (d >= 8) "${pool.random()} ${pool.random()}" else pool.random()
        val enc = plain.map { ch ->
            if (ch in 'a'..'z') 'a' + ((ch - 'a' + shift) % 26) else ch
        }.joinToString("")
        val prompt = "Шифр Цезаря: каждая буква латинского алфавита сдвинута вперёд на $shift.\n" +
            "Зашифровано: ${enc.uppercase()}\nВведите исходный текст латиницей."
        return Task(TaskType.CAESAR, prompt, plain, difficulty = d)
    }

    private fun sequence(d: Int): Task {
        val r = Random
        val all = ArrayList<Long>()
        var shown = 5
        when {
            d <= 2 -> {
                val start = r.nextInt(1, 21)
                val step = r.nextInt(1, 6 + d)
                for (i in 0..5) all.add((start + step * i).toLong())
            }
            d <= 5 -> {
                if (r.nextBoolean()) {
                    val start = r.nextInt(5, 51)
                    val step = r.nextInt(6, 16)
                    for (i in 0..5) all.add((start + step * i).toLong())
                } else {
                    var v = r.nextInt(1, 4).toLong()
                    val ratio = r.nextInt(2, 4)
                    for (i in 0..5) {
                        all.add(v); v *= ratio
                    }
                }
            }
            d <= 8 -> {
                shown = 6
                when (r.nextInt(3)) {
                    0 -> {
                        var v = r.nextInt(1, 11).toLong()
                        val d0 = r.nextInt(1, 5)
                        val k = r.nextInt(1, 4)
                        for (i in 0..6) {
                            all.add(v); v += d0 + i * k
                        }
                    }
                    1 -> {
                        var a = r.nextInt(1, 6).toLong()
                        var b = r.nextInt(1, 6).toLong()
                        for (i in 0..6) {
                            all.add(a)
                            val n = a + b; a = b; b = n
                        }
                    }
                    else -> {
                        val n0 = r.nextInt(2, 8)
                        val c = r.nextInt(-5, 10)
                        for (i in 0..6) all.add(((n0 + i) * (n0 + i) + c).toLong())
                    }
                }
            }
            else -> {
                shown = 7
                when (r.nextInt(3)) {
                    0 -> {
                        val s1 = r.nextInt(2, 10)
                        val step = r.nextInt(2, 8)
                        val s2 = r.nextInt(1, 5)
                        val ratio = r.nextInt(2, 4)
                        for (i in 0..7) {
                            if (i % 2 == 0) all.add((s1 + step * (i / 2)).toLong())
                            else {
                                var v = s2.toLong()
                                repeat(i / 2) { v *= ratio }
                                all.add(v)
                            }
                        }
                    }
                    1 -> {
                        val n0 = r.nextInt(2, 6)
                        for (i in 0..7) {
                            val n = (n0 + i).toLong()
                            all.add(n * n * n - n)
                        }
                    }
                    else -> {
                        var v = r.nextInt(1, 6).toLong()
                        var p = 1L
                        for (i in 0..7) {
                            all.add(v); v += p; p *= 2
                        }
                    }
                }
            }
        }
        val shownList = all.take(shown).joinToString(", ")
        return Task(
            TaskType.SEQUENCE, "Продолжите ряд, введите следующее число:\n$shownList, ?",
            all[shown].toString(), difficulty = d
        )
    }

    private fun captcha(d: Int): Task {
        val len = 4 + d * 6 / 10
        val alphabet = when {
            d < 3 -> "23456789"
            d < 6 -> "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            else -> "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"
        }
        val text = (1..len).map { alphabet.random() }.joinToString("")
        val cs = d >= 6
        val prompt = "Введите символы с картинки" + if (cs) " (регистр важен)" else ""
        return Task(TaskType.CAPTCHA, prompt, text, captcha = text, difficulty = d, caseSensitive = cs)
    }
}