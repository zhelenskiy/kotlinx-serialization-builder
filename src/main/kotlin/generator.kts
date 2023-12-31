import java.io.File

private val tab = "    "
private val generateIndices = 0u..32u

fun StringBuilder.generate(n: UInt) {
    operator fun String.unaryPlus() = appendLine(this)

    +"open class ClassKSerializer$n<${(listOf("T") + (1u..n).map { "R$it" }).joinToString(", ")}>("
    +"${tab}private val descriptorName: String,"
    for (i in 1u..n) {
        +"${tab}private val serializerPart$i: Property<T, R$i>,"
    }
    +"${tab}private val build: (${(1u..n).joinToString(", ") { "R$it" }}) -> T,"
    +") : KSerializer<T> {"

    +"${tab}final override val descriptor: SerialDescriptor = buildClassSerialDescriptor(descriptorName) {"
    for (i in 1u..n) {
        +"${tab}${tab}element(serializerPart${i}.name, serializerPart${i}.serializer.descriptor)"
    }
    +"${tab}}"

    +""

    +"${tab}final override fun serialize(encoder: Encoder, value: T) = encoder.encodeStructure(descriptor) {"
    for (i in 1u..n) {
        +"${tab}${tab}encodeSerializableElement(descriptor, ${i - 1u}, serializerPart${i}.serializer, serializerPart${i}.generator(value))"
    }
    +"${tab}}"

    +""

    +"${tab}@OptIn(ExperimentalSerializationApi::class)"
    +"${tab}final override fun deserialize(decoder: Decoder): T = decoder.decodeStructure(descriptor) {"
    if (n == 0u) {
        +"${tab}${tab}build()"
    } else {
        +"${tab}${tab}if (decodeSequentially()) {"
        +"${tab}${tab}${tab}build("
        for (i in 1u..n) {
            +"${tab}${tab}${tab}${tab}decodeSerializableElement(descriptor, ${i - 1u}, serializerPart${i}.serializer),"
        }
        +"${tab}${tab}${tab})"
        +"${tab}${tab}} else {"
        for (i in 1u..n) {
            +"${tab}${tab}${tab}var value$i: R$i? = null"
        }
        +"${tab}${tab}${tab}while (true) {"
        +"${tab}${tab}${tab}${tab}when (val index = decodeElementIndex(descriptor)) {"
        for (i in 1u..n) {
            +"${tab}${tab}${tab}${tab}${tab}${i - 1u} -> value$i = decodeSerializableElement(descriptor, ${i - 1u}, serializerPart${i}.serializer)"
        }
        +"${tab}${tab}${tab}${tab}${tab}CompositeDecoder.DECODE_DONE -> break"
        +"${tab}${tab}${tab}${tab}${tab}else -> error(\"Unexpected index: \$index\")"
        +"${tab}${tab}${tab}${tab}}"
        +"${tab}${tab}${tab}}"
        +"${tab}${tab}${tab}return@decodeStructure build("
        for (i in 1u..n) {
            +"${tab}${tab}${tab}${tab}value${i} ?: error(\"Absent field: \${serializerPart${i}.name}\"),"
        }
        +"${tab}${tab}${tab})"
        +"${tab}${tab}}"
    }
    +"${tab}}"

    +"}"
    
    +""

    +"fun <${(listOf("T") + (1u..n).map { "R$it" }).joinToString(", ")}> classSerializer("
    +"${tab}descriptorName: String,"
    for (i in 1u..n) {
        +"${tab}serializerPart$i: Property<T, R$i>,"
    }
    +"${tab}build: (${(1u..n).joinToString(", ") { "R$it" }}) -> T,"
    +") : KSerializer<T> = ClassKSerializer$n("
    +"${tab}descriptorName,"
    for (i in 1u..n) {
        +"${tab}serializerPart$i,"
    }
    +"${tab}build,"
    +")"
}

buildString {
    operator fun String.unaryPlus() = appendLine(this)
    +"@file:Suppress(\"DuplicatedCode\")"
    +""
    +"import kotlinx.serialization.ExperimentalSerializationApi"
    +"import kotlinx.serialization.KSerializer"
    +"import kotlinx.serialization.descriptors.SerialDescriptor"
    +"import kotlinx.serialization.descriptors.buildClassSerialDescriptor"
    +"import kotlinx.serialization.encoding.CompositeDecoder"
    +"import kotlinx.serialization.encoding.Decoder"
    +"import kotlinx.serialization.encoding.Encoder"
    +"import kotlinx.serialization.encoding.decodeStructure"
    +"import kotlinx.serialization.encoding.encodeStructure"
    +"import kotlin.reflect.KProperty1"
    +""
    +"// This file is generated by a script"
    +""
    +""
    +"@OptIn(ExperimentalSerializationApi::class)"
    +"data class Property<T, R>("
    +"${tab}val name: String,"
    +"${tab}val serializer: KSerializer<R>,"
    +"${tab}val generator: (T) -> R,"
    +") {"
    +"${tab}constructor(property: KProperty1<T, R>, serializer: KSerializer<R>) : this(property.name, serializer, property)"
    +"}"
    for (n in generateIndices) {
        +""
        generate(n)
    }
}.also(File("ClassSerializer.kt")::writeText)
