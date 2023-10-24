import ClassTests.GenericDelegate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ClassTests {
    @Test
    fun arity0() {
        val unique = Any()
        val serializer = classSerializer("Empty") { unique }
        val expected = "{}"
        assertEquals(expected, Json.encodeToString(serializer, unique))
        assertEquals(unique, Json.decodeFromString(serializer, expected))
    }

    @Serializable
    data class Color(val red: UByte, val green: UByte, val blue: UByte)

    @Test
    fun arity1() {
        val color = Color(245u, 250u, 254u)
        val serializer = classSerializer(
            descriptorName = "Color",
            serializerPart1 = Property(name = "argb", serializer = Int.serializer()) {
                it.red.toInt().shl(16) xor it.green.toInt().shl(8) xor it.blue.toInt()
            },
            build = { Color(it.shr(16).toUByte(), it.shr(8).toUByte(), it.toUByte()) }
        ) 
        val expected = "{\"argb\":${245 * 65536 + 250 * 256 + 254}}"
        assertEquals(expected, Json.encodeToString(serializer, color))
        assertEquals(color, Json.decodeFromString(serializer, expected))
    }

    @Test
    fun arity3() {
        val color = Color(245u, 250u, 254u)
        val serializer = classSerializer(
            descriptorName = "Color",
            Property(name = "r", serializer = UByte.serializer()) { it.red },
            Property(name = "g", serializer = UByte.serializer()) { it.green },
            Property(name = "b", serializer = UByte.serializer()) { it.blue },
            build = ::Color
        ) 
        val expected = "{\"r\":245,\"g\":250,\"b\":254}"
        assertEquals(expected, Json.encodeToString(serializer, color))
        assertEquals(color, Json.decodeFromString(serializer, expected))
    }

    @Test
    fun arity3_property() {
        val color = Color(245u, 250u, 254u)
        val serializer = classSerializer(
            descriptorName = "Color",
            Property(property = Color::red, serializer = UByte.serializer()),
            Property(property = Color::green, serializer = UByte.serializer()),
            Property(property = Color::blue, serializer = UByte.serializer()),
            build = ::Color
        ) 
        val expected = "{\"red\":245,\"green\":250,\"blue\":254}"
        assertEquals(expected, Json.encodeToString(serializer, color))
        assertEquals(color, Json.decodeFromString(serializer, expected))
    }

    @Test
    fun arity3_default() {
        val color = Color(245u, 250u, 254u)
        val expected = "{\"red\":245,\"green\":250,\"blue\":254}"
        assertEquals(expected, Json.encodeToString(color))
        assertEquals(color, Json.decodeFromString(expected))
    }

    @Serializable(with = GenericDelegate::class)
    private data class Point<T>(val x: T, val y: T)

    private class GenericDelegate<T>(serializer: KSerializer<T>) : ClassKSerializer2<Point<T>, T, T>(
        descriptorName = "Point",
        Property(property = Point<T>::x, serializer = serializer),
        Property(property = Point<T>::y, serializer = serializer),
        build = ::Point
    )

    @Test
    fun genericClass() {
        val x = Point(1, 2)
        val expected = "{\"x\":1,\"y\":2}"
        assertEquals(expected, Json.encodeToString(x))
        assertEquals(x, Json.decodeFromString(expected))
    }
}
