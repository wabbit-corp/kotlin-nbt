package one.wabbit.nbt

object SNBT {
    fun parse(text: String): Tag = Parser(text).parse()

    private class Parser(
        private val text: String,
    ) {
        private var index: Int = 0

        private fun peek(): Char? = text.getOrNull(index)

        private fun read(): Char = text[index++]

        private fun skipWhitespace() {
            while (peek()?.isWhitespace() == true) {
                read()
            }
        }

        private fun expect(ch: Char) {
            skipWhitespace()
            require(peek() == ch) { "Expected '$ch' in SNBT." }
            read()
        }

        private fun readHexDigits(count: Int): String {
            val endIndex = index + count
            require(endIndex <= text.length) { "Incomplete hexadecimal escape in SNBT string." }
            val digits = text.substring(index, endIndex)
            require(digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                "Invalid hexadecimal escape in SNBT string."
            }
            index = endIndex
            return digits
        }

        private fun parseQuotedString(): String {
            skipWhitespace()
            val quote = peek()
            require(quote == '"' || quote == '\'') { "Expected quoted SNBT string." }
            read()

            val builder = StringBuilder()
            while (true) {
                when (val current = peek()) {
                    null -> throw IllegalArgumentException("Unterminated SNBT string.")
                    quote -> {
                        read()
                        return builder.toString()
                    }
                    '\\' -> {
                        read()
                        when (val escaped = peek()) {
                            null -> throw IllegalArgumentException("Invalid escape at end of SNBT string.")
                            '\\', '"', '\'' -> {
                                read()
                                builder.append(escaped)
                            }
                            'b' -> {
                                read()
                                builder.append('\b')
                            }
                            's' -> {
                                read()
                                builder.append(' ')
                            }
                            't' -> {
                                read()
                                builder.append('\t')
                            }
                            'n' -> {
                                read()
                                builder.append('\n')
                            }
                            'f' -> {
                                read()
                                builder.append('\u000C')
                            }
                            'r' -> {
                                read()
                                builder.append('\r')
                            }
                            'x' -> {
                                read()
                                builder.append(readHexDigits(2).toInt(16).toChar())
                            }
                            'u' -> {
                                read()
                                builder.append(readHexDigits(4).toInt(16).toChar())
                            }
                            'U' -> {
                                read()
                                val codePoint = readHexDigits(8).toInt(16)
                                builder.appendCodePoint(codePoint)
                            }
                            else -> {
                                read()
                                builder.append(escaped)
                            }
                        }
                    }
                    else -> {
                        read()
                        builder.append(current)
                    }
                }
            }
        }

        private fun parseRelaxedToken(terminators: Set<Char>): String {
            skipWhitespace()
            val builder = StringBuilder()
            val closingDelimiters = mutableListOf<Char>()
            var quote: Char? = null
            var escaped = false

            while (true) {
                val current = peek() ?: break
                if (quote != null) {
                    read()
                    builder.append(current)
                    if (escaped) {
                        escaped = false
                    } else if (current == '\\') {
                        escaped = true
                    } else if (current == quote) {
                        quote = null
                    }
                    continue
                }

                if (closingDelimiters.isEmpty() && current in terminators) {
                    break
                }

                read()
                builder.append(current)

                when (current) {
                    '"', '\'' -> quote = current
                    '{' -> closingDelimiters.add('}')
                    '[' -> closingDelimiters.add(']')
                    '}', ']' -> {
                        if (closingDelimiters.isNotEmpty() && closingDelimiters.last() == current) {
                            closingDelimiters.removeLast()
                        }
                    }
                }
            }

            return builder.toString().trimEnd().also {
                require(it.isNotEmpty()) { "Expected SNBT token." }
            }
        }

        private fun parseKey(): String {
            skipWhitespace()
            return when (peek()) {
                '"', '\'' -> parseQuotedString()
                else -> parseRelaxedToken(setOf(':'))
            }
        }

        private fun parseSignedInteger(token: String): Long? {
            if (token.isEmpty()) {
                return null
            }

            val isNegative = token.startsWith('-')
            val unsigned =
                when {
                    token.startsWith('-') || token.startsWith('+') -> token.substring(1)
                    else -> token
                }

            fun parseRadix(prefix: String, radix: Int): Long? {
                if (!unsigned.startsWith(prefix, ignoreCase = true)) {
                    return null
                }

                val digits = unsigned.substring(2)
                if (digits.isEmpty()) {
                    return null
                }

                return runCatching { digits.toLong(radix) }.getOrNull()?.let {
                    if (isNegative) {
                        -it
                    } else {
                        it
                    }
                }
            }

            return parseRadix("0x", 16) ?: parseRadix("0b", 2) ?: token.toLongOrNull()
        }

        private fun parseNumberOrIdentifier(token: String): Tag {
            val lower = token.lowercase()

            fun <T : Tag> parseSuffix(suffix: String, parse: (String) -> T?): T? =
                if (lower.endsWith(suffix)) {
                    parse(token.dropLast(suffix.length))
                } else {
                    null
                }

            return when (lower) {
                "end" -> EndTag
                else ->
                    parseSuffix("b") { parseSignedInteger(it)?.toByte()?.let(::ByteTag) }
                        ?: parseSuffix("s") { parseSignedInteger(it)?.toShort()?.let(::ShortTag) }
                        ?: parseSuffix("l") { parseSignedInteger(it)?.let(::LongTag) }
                        ?: parseSuffix("f") { it.toFloatOrNull()?.let(::FloatTag) }
                        ?: parseSuffix("d") { it.toDoubleOrNull()?.let(::DoubleTag) }
                        ?: parseSignedInteger(token)?.let { value ->
                            if (value in Int.MIN_VALUE..Int.MAX_VALUE) {
                                IntTag(value.toInt())
                            } else {
                                StringTag(token)
                            }
                        }
                        ?: token.toDoubleOrNull()?.let(::DoubleTag)
                        ?: StringTag(token)
            }
        }

        private fun parseTypedArray(kind: Char): Tag {
            read()
            expect(';')
            skipWhitespace()

            fun gather(): List<Tag> {
                val values = mutableListOf<Tag>()
                while (true) {
                    skipWhitespace()
                    when (peek()) {
                        ']' -> {
                            read()
                            return values
                        }
                        else -> {
                            values += parseValue()
                            skipWhitespace()
                            when (peek()) {
                                ',' -> read()
                                ']' -> {
                                    read()
                                    return values
                                }
                                else -> throw IllegalArgumentException("Expected ',' or ']' in typed SNBT array.")
                            }
                        }
                    }
                }
            }

            val values = gather()
            return when (kind) {
                'B' ->
                    ByteArrayTag(
                        values.map {
                            when (it) {
                                is ByteTag -> it.value
                                is IntTag -> it.value.toByte()
                                else -> throw IllegalArgumentException("Invalid byte array element: $it")
                            }
                        }.toByteArray()
                    )
                'I' ->
                    IntArrayTag(
                        values.map {
                            when (it) {
                                is IntTag -> it.value
                                else -> throw IllegalArgumentException("Invalid int array element: $it")
                            }
                        }.toIntArray()
                    )
                'L' ->
                    LongArrayTag(
                        values.map {
                            when (it) {
                                is LongTag -> it.value
                                is IntTag -> it.value.toLong()
                                else -> throw IllegalArgumentException("Invalid long array element: $it")
                            }
                        }.toLongArray()
                    )
                else -> throw IllegalArgumentException("Unsupported typed SNBT array kind '$kind'.")
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun listTagOf(values: List<Tag>): ListTag<Tag> {
            if (values.isEmpty()) {
                return ListTag(TagType.End as TagType<Tag>, mutableListOf())
            }

            val elementType = values.first().type as TagType<Tag>
            require(values.all { it.type == elementType }) { "NBT lists must be homogeneous." }
            return ListTag(elementType, values.toMutableList())
        }

        private fun parseList(): Tag {
            expect('[')
            skipWhitespace()

            val first = peek()
            val second = text.getOrNull(index + 1)
            if (first != null && second == ';' && first in charArrayOf('B', 'I', 'L')) {
                return parseTypedArray(first)
            }

            val values = mutableListOf<Tag>()
            while (true) {
                skipWhitespace()
                when (peek()) {
                    ']' -> {
                        read()
                        return listTagOf(values)
                    }
                    else -> {
                        values += parseValue()
                        skipWhitespace()
                        when (peek()) {
                            ',' -> read()
                            ']' -> {
                                read()
                                return listTagOf(values)
                            }
                            else -> throw IllegalArgumentException("Expected ',' or ']' in SNBT list.")
                        }
                    }
                }
            }
        }

        private fun parseCompound(): Tag {
            expect('{')
            val entries = linkedMapOf<String, Tag>()
            while (true) {
                skipWhitespace()
                when (peek()) {
                    '}' -> {
                        read()
                        return CompoundTag(entries)
                    }
                    else -> {
                        val key = parseKey()
                        expect(':')
                        entries[key] = parseValue()
                        skipWhitespace()
                        when (peek()) {
                            ',' -> read()
                            '}' -> {
                                read()
                                return CompoundTag(entries)
                            }
                            else -> throw IllegalArgumentException("Expected ',' or '}' in SNBT compound.")
                        }
                    }
                }
            }
        }

        private fun parseValue(): Tag {
            skipWhitespace()
            return when (peek()) {
                '{' -> parseCompound()
                '[' -> parseList()
                '"', '\'' -> StringTag(parseQuotedString())
                null -> throw IllegalArgumentException("Unexpected end of SNBT input.")
                else -> parseNumberOrIdentifier(parseRelaxedToken(setOf(',', ']', '}')))
            }
        }

        fun parse(): Tag {
            val value = parseValue()
            skipWhitespace()
            require(index == text.length) { "Trailing characters found after SNBT value." }
            return value
        }
    }
}

private fun StringBuilder.appendCodePoint(codePoint: Int) {
    when {
        codePoint < 0 || codePoint > 0x10FFFF ->
            throw IllegalArgumentException("Invalid Unicode code point in SNBT string.")
        codePoint <= 0xFFFF -> append(codePoint.toChar())
        else -> {
            val adjusted = codePoint - 0x10000
            val highSurrogate = ((adjusted ushr 10) + 0xD800).toChar()
            val lowSurrogate = ((adjusted and 0x3FF) + 0xDC00).toChar()
            append(highSurrogate)
            append(lowSurrogate)
        }
    }
}
