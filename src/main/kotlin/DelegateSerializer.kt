import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

open class DelegatedKSerializer<T, R>(
    private val delegate: KSerializer<R>,
    private val save: (T) -> R,
    private val restore: (R) -> T,
    descriptorName: String? = null,
) : KSerializer<T> {
    @OptIn(ExperimentalSerializationApi::class)
    final override val descriptor: SerialDescriptor =
        if (descriptorName.isNullOrBlank() || descriptorName == delegate.descriptor.serialName) {
            delegate.descriptor
        } else when (val kind = delegate.descriptor.kind) {
            is PrimitiveKind -> PrimitiveSerialDescriptor(descriptorName, kind)
            else -> SerialDescriptor(descriptorName, delegate.descriptor)
        }

    final override fun serialize(encoder: Encoder, value: T) {
        delegate.serialize(encoder, save(value))
    }

    final override fun deserialize(decoder: Decoder): T {
        return restore(delegate.deserialize(decoder))
    }
}

fun <T, R> delegatedSerializer(
    delegate: KSerializer<R>,
    save: (T) -> R,
    restore: (R) -> T,
    descriptorName: String? = null,
): KSerializer<T> = DelegatedKSerializer(delegate, save, restore, descriptorName)
