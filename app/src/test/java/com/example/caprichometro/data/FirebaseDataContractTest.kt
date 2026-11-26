package com.example.caprichometro.data

import com.example.caprichometro.domain.model.FriendshipStatus
import com.example.caprichometro.domain.model.UsernameRules
import com.example.caprichometro.infrastructure.data.toCapriceOrNull
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Date

class FirebaseDataContractTest {

    private fun resolveContractsDir(): File {
        val candidates = listOf(
            File("../shared-docs/contracts"),
            File("shared-docs/contracts")
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate shared-docs/contracts from ${File(".").absolutePath}")
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadFixture(fileName: String): Map<String, Any?> {
        val file = File(resolveContractsDir(), fileName)
        assertThat(file).exists()
        val parsed = SimpleJsonParser(file.readText(Charsets.UTF_8)).parseValue()
        return parsed as Map<String, Any?>
    }

    private fun mapToSnapshot(data: Map<String, Any?>): DocumentSnapshot {
        val docId = data["_documentId"] as? String ?: ""
        val snap = mockk<DocumentSnapshot>(relaxed = true)
        every { snap.id } returns docId
        every { snap.exists() } returns true
        every { snap.get(any<String>()) } answers {
            data[firstArg()]
        }
        every { snap.getString(any()) } answers {
            data[firstArg()] as? String
        }
        every { snap.getBoolean(any()) } answers {
            data[firstArg()] as? Boolean
        }
        every { snap.getLong(any()) } answers {
            (data[firstArg()] as? Number)?.toLong()
        }
        every { snap.getTimestamp(any()) } answers {
            val key = firstArg<String>()
            val epochMs = (data[key] as? Number)?.toLong()
                ?: (data["${key}EpochMillis"] as? Number)?.toLong()
            epochMs?.let { Timestamp(Date(it)) }
        }
        every { snap.getDate(any()) } answers {
            val key = firstArg<String>()
            val epochMs = (data[key] as? Number)?.toLong()
                ?: (data["${key}EpochMillis"] as? Number)?.toLong()
            epochMs?.let(::Date)
        }
        return snap
    }

    @Test
    fun `caprice_private fixture matches Caprice contract mapping`() {
        val data = loadFixture("caprice_private.json")
        val caprice = mapToSnapshot(data).toCapriceOrNull("ContractTest")

        assertThat(caprice).isNotNull
        assertThat(caprice!!.id).isEqualTo("cap_contract_private")
        assertThat(caprice.userId).isEqualTo("uid_author_1")
        assertThat(caprice.type).isEqualTo("Compra")
        assertThat(caprice.question).isEqualTo("¿Me compro estas zapatillas?")
        assertThat(caprice.answer1).isEqualTo("Sí, de cabeza")
        assertThat(caprice.answer2).isEqualTo("Mejor ahorra")
        assertThat(caprice.price).isEqualTo(89.95)
        assertThat(caprice.timestamp).isEqualTo(1_732_640_000_000L)
        assertThat(caprice.hasImage).isTrue()
        assertThat(caprice.visibleToUserIds).containsExactly("uid_author_1", "uid_friend_2")
        assertThat(caprice.locationCell).isEqualTo("v1_2608_3525")
        assertThat(caprice.nearbyCell).isNull()
        assertThat(caprice.finalDecision).isEqualTo(1)
        assertThat(caprice.finalDecisionAt).isEqualTo(1_732_640_420_000L)
    }

    @Test
    fun `caprice_nearby fixture matches General discovery contract`() {
        val data = loadFixture("caprice_nearby.json")
        val caprice = mapToSnapshot(data).toCapriceOrNull("ContractTest")

        assertThat(caprice).isNotNull
        assertThat(caprice!!.id).isEqualTo("cap_contract_nearby")
        assertThat(caprice.nearbyCell).isEqualTo("v1_2608_3525")
        assertThat(caprice.locationCell).isEqualTo("v1_2608_3525")
        assertThat(caprice.hasImage).isFalse()
        assertThat(caprice.price).isNull()
        assertThat(caprice.visibleToUserIds).isEmpty()
    }

    @Test
    fun `caprice_legacy_image fixture infers hasImage from legacy imageUrl`() {
        val data = loadFixture("caprice_legacy_image.json")
        val caprice = mapToSnapshot(data).toCapriceOrNull("ContractTest")

        assertThat(caprice).isNotNull
        assertThat(caprice!!.id).isEqualTo("cap_contract_legacy")
        assertThat(caprice.imageUrl).startsWith("data:image/webp;base64,")
        assertThat(caprice.hasImage).isTrue()
    }

    @Test
    fun `user_profile, friendship_pending and vote_option fixtures satisfy contract invariants`() {
        val profile = loadFixture("user_profile.json")
        val username = profile["username"] as String
        assertThat(UsernameRules.isValid(username)).isTrue()
        assertThat(profile["bio"] as String).hasSizeLessThanOrEqualTo(160)
        assertThat(profile["photoUrl"] as String).startsWith("data:image/webp;base64,")
        assertThat(profile["locationCell"] as String).startsWith("v1_")
        assertThat((profile["createdAtEpochMillis"] as Number).toLong()).isPositive()

        val friendship = loadFixture("friendship_pending.json")
        @Suppress("UNCHECKED_CAST")
        val memberIDs = friendship["memberIDs"] as List<String>
        assertThat(memberIDs).containsExactly("uid_a", "uid_b")
        assertThat(friendship["_documentId"]).isEqualTo("${memberIDs[0]}_${memberIDs[1]}")
        assertThat(friendship["requestedBy"]).isIn(memberIDs)
        assertThat(friendship["status"]).isEqualTo(FriendshipStatus.PENDING.value)

        val vote = loadFixture("vote_option.json")
        assertThat((vote["option"] as Number).toInt()).isIn(1, 2)
        assertThat(vote["voterId"]).isEqualTo(vote["_documentId"])
        assertThat((vote["timestamp"] as Number).toLong()).isPositive()
    }

    private class SimpleJsonParser(private val input: String) {
        private var pos = 0

        fun parseValue(): Any? {
            skipWhitespace()
            if (pos >= input.length) error("Unexpected end of JSON")
            return when (val ch = input[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                '-', in '0'..'9' -> parseNumber()
                else -> error("Unexpected character '$ch' at $pos")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            pos++ // {
            val map = linkedMapOf<String, Any?>()
            skipWhitespace()
            if (input[pos] == '}') {
                pos++
                return map
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                require(input[pos] == ':') { "Expected ':' at $pos" }
                pos++
                val value = parseValue()
                map[key] = value
                skipWhitespace()
                when (input[pos]) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return map
                    }
                    else -> error("Expected ',' or '}' at $pos")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            pos++ // [
            val list = mutableListOf<Any?>()
            skipWhitespace()
            if (input[pos] == ']') {
                pos++
                return list
            }
            while (true) {
                list += parseValue()
                skipWhitespace()
                when (input[pos]) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return list
                    }
                    else -> error("Expected ',' or ']' at $pos")
                }
            }
        }

        private fun parseString(): String {
            require(input[pos] == '"') { "Expected '\"' at $pos" }
            pos++
            val sb = StringBuilder()
            while (pos < input.length) {
                val ch = input[pos++]
                when (ch) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        val esc = input[pos++]
                        sb.append(
                            when (esc) {
                                '"', '\\', '/' -> esc
                                'b' -> '\b'
                                'f' -> '\u000C'
                                'n' -> '\n'
                                'r' -> '\r'
                                't' -> '\t'
                                'u' -> {
                                    val hex = input.substring(pos, pos + 4)
                                    pos += 4
                                    hex.toInt(16).toChar()
                                }
                                else -> esc
                            }
                        )
                    }
                    else -> sb.append(ch)
                }
            }
            error("Unterminated string")
        }

        private fun parseBoolean(): Boolean {
            return if (input.startsWith("true", pos)) {
                pos += 4
                true
            } else if (input.startsWith("false", pos)) {
                pos += 5
                false
            } else {
                error("Invalid boolean at $pos")
            }
        }

        private fun parseNull(): Any? {
            require(input.startsWith("null", pos)) { "Invalid null at $pos" }
            pos += 4
            return null
        }

        private fun parseNumber(): Number {
            val start = pos
            while (pos < input.length && input[pos] in "-0123456789.eE+") {
                pos++
            }
            val raw = input.substring(start, pos)
            return if (raw.contains('.') || raw.contains('e') || raw.contains('E')) {
                raw.toDouble()
            } else {
                raw.toLong()
            }
        }

        private fun skipWhitespace() {
            while (pos < input.length && input[pos].isWhitespace()) {
                pos++
            }
        }
    }
}
