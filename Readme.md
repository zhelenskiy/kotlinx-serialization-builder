# kotlinx.serialization helpers

## kotlinx.serialization

**kotlinx.serialization** is a powerful multiplatform library for creating serializers and deserializers for Kotlin. It provides builtin support for several [serialization formats](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/formats.md) such as JSON, ProtoBuf, CBOR. It also supports custom formats. Community has added support to other formats, such as [HOCON, YAML, TOML, XML](https://github.com/Kotlin/kotlinx.serialization/blob/master/formats/README.md).

**kotlinx.serialization** uses code generation to automatically create serializers (as reflection is not fully available outside JVM):

```kotlin
@Serializable
data class Point(val x: Double, val y: Double)
```

The library also offers ways to configure property names in the serialized objects and other ways to configure options.

However, this automatic generation is not possible if you have no primary constructor or some of its parameters are not backed with properties. You may also want to create your own serializer. For example, you store `Color` effectively in inline class but you don't want to expose its internal representation in JSONs:
```kotlin
@JvmInline
value class Color private constructor(internal val value: Int) {
    constructor(red: UByte, green: UByte, blue: UByte) : this(red.toInt().shl(16) xor green.toInt().shl(8) xor blue.toInt())
    
    val red get() = value.shr(16).toUByte()
    val green get() = value.shr(8).toUByte()
    val blue get() = value.toUByte()
}
```

## Problem

The downside of the power and universality is that handwritten `KSerializer`s are very [verbose](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serializers.md#hand-written-composite-serializer):
```kotlin
object ColorAsObjectSerializer : KSerializer<Color> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Color") {
            element<Int>("red")
            element<Int>("green")
            element<Int>("blue")
        }

    override fun serialize(encoder: Encoder, value: Color) =
        encoder.encodeStructure(descriptor) {
            encodeIntElement(descriptor, 0, (value.value shr 16) and 0xff)
            encodeIntElement(descriptor, 1, (value.value shr 8) and 0xff)
            encodeIntElement(descriptor, 2, value.value and 0xff)
        }

    override fun deserialize(decoder: Decoder): Color =
        decoder.decodeStructure(descriptor) {
            var r = -1
            var g = -1
            var b = -1
            if (decodeSequentially()) { // sequential decoding protocol
                r = decodeIntElement(descriptor, 0)
                g = decodeIntElement(descriptor, 1)
                b = decodeIntElement(descriptor, 2)
            } else while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> r = decodeIntElement(descriptor, 0)
                    1 -> g = decodeIntElement(descriptor, 1)
                    2 -> b = decodeIntElement(descriptor, 2)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            require(r in 0..255 && g in 0..255 && b in 0..255)
            Color(r.toUByte(), g.toUByte(), b.toUByte())
        }
}
```

It is suggested to delegate to a wrapper class to simplify:

```kotlin

object ColorSerializer : KSerializer<Color> {
    @Serializable
    @SerialName("Color")
    private class ColorSurrogate(val red: Int, val green: Int, val blue: Int) {
        init {
            require(red in 0..255 && green in 0..255 && blue in 0..255)
        }
    }
    
    override val descriptor: SerialDescriptor = ColorSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Color) {
        val surrogate = ColorSurrogate((value.value shr 16) and 0xff, (value.value shr 8) and 0xff, value.value and 0xff)
        encoder.encodeSerializableValue(ColorSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): Color {
        val surrogate = decoder.decodeSerializableValue(ColorSurrogate.serializer())
        return Color(surrogate.red.toUByte(), surrogate.green.toUByte(), surrogate.blue.toUByte())
    }
}
```

It is much better but still verbose.

## Solution

The goal of the current project is to provide proper generators for delegating and composing serializers.

These generators are available both as functions and classes. The prior is more convenient for simple usage while the latter can be used to create [custom generic serializers](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serializers.md#custom-serializers-for-a-generic-type).

### Delegating serializer
#### Simple usage
```kotlin
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
```
#### Generic usage
```kotlin
@Serializable(with = GenericDelegate::class)
private data class Wrapper<T>(val x: T)

private class GenericDelegate<T>(serializer: KSerializer<T>) : DelegatedKSerializer<Wrapper<T>, T>(
    delegate = serializer,
    save = { it.x },
    restore = { Wrapper(it) },
    descriptorName = Wrapper::class.qualifiedName
)

val x = Wrapper(3)
val expected = "3"
assertEquals(expected, Json.encodeToString(x))
assertEquals(x, Json.decodeFromString(expected))
```

### Class serializer
Example class:
```kotlin
data class Color(val red: UByte, val green: UByte, val blue: UByte)
```
#### Simple usages
```kotlin
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
```
##### Property syntax
```kotlin
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
```
#### Generic usage
```kotlin
@Serializable(with = GenericDelegate::class)
private data class Point<T>(val x: T, val y: T)

private class GenericDelegate<T>(serializer: KSerializer<T>) : ClassKSerializer2<Point<T>, T, T>(
    descriptorName = "Point",
    Property(property = Point<T>::x, serializer = serializer),
    Property(property = Point<T>::y, serializer = serializer),
    build = ::Point
)

val x = Point(1, 2)
val expected = "{\"x\":1,\"y\":2}"
assertEquals(expected, Json.encodeToString(x))
assertEquals(x, Json.decodeFromString(expected))
```
