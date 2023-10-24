import DelegateTests.GenericDelegate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DelegateTests {
    @Test
    fun primitiveDelegate() {
        val int2String = delegatedSerializer<Int, String>(
            delegate = String.Companion.serializer(),
            descriptorName = "MyInt1",
            save = { "-$it" },
            restore = { it.drop(1).toInt() },
        )
        val x = 3
        val expected = "\"-3\""
        assertEquals(expected, Json.encodeToString(int2String, x))
        assertEquals(x, Json.decodeFromString(int2String, expected))
    }

    @Test
    fun complexDelegate() {
        val int2List = delegatedSerializer<Int, List<Int>>(
            delegate = ListSerializer(Int.serializer()),
            descriptorName = "MyInt2",
            save = ::listOf,
            restore = List<Int>::first,
        )
        val x = 3
        val expected = "[3]"
        assertEquals(expected, Json.encodeToString(int2List, x))
        assertEquals(x, Json.decodeFromString(int2List, expected))
    }

    @Serializable(with = GenericDelegate::class)
    private data class Wrapper<T>(val x: T)

    private class GenericDelegate<T>(serializer: KSerializer<T>) : DelegatedKSerializer<Wrapper<T>, T>(
        delegate = serializer,
        save = { it.x },
        restore = { Wrapper(it) },
        descriptorName = Wrapper::class.qualifiedName
    )
    
    @Test
    fun genericDelegate() {
        val x = Wrapper(3)
        val expected = "3"
        assertEquals(expected, Json.encodeToString(x))
        assertEquals(x, Json.decodeFromString(expected))
    }
}
