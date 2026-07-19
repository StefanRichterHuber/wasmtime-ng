package io.github.stefanrichterhuber.witparser;

/**
 * Every WIT type-system shape this pipeline understands, classified down to
 * the fixed set of Java types {@link WitValueType} maps them to. Nested
 * structure (record field types, variant case payload types, etc.) isn't
 * tracked -- the Java signature for a shape is the same regardless of what's
 * nested inside it (e.g. every {@code record} is {@code Map<String,Object>},
 * whether it has two fields or twenty), matching how the hand-written
 * {@code wasip2} contexts already represent these shapes.
 */
public enum WitTypeKind {
    BOOL,
    S8,
    U8,
    S16,
    U16,
    S32,
    U32,
    S64,
    U64,
    F32,
    F64,
    CHAR,
    STRING,

    /** {@code list<u8>} specifically -- maps to {@code byte[]} rather than {@code List}. */
    LIST_U8,
    /** Any other {@code list<T>} -- maps to {@code java.util.List}. */
    LIST,
    /** {@code option<T>} -- maps to {@code java.util.Optional}. */
    OPTION,
    /** {@code result<T, E>} or bare {@code result} -- maps to {@code component.WitResult}. */
    RESULT,
    /** {@code tuple<...>} -- maps to {@code Object[]}. */
    TUPLE,
    /** {@code record} -- maps to {@code java.util.Map<String, Object>}. */
    RECORD,
    /** {@code variant} -- maps to {@code component.WitVariant}. */
    VARIANT,
    /** {@code enum} -- maps to {@code component.WitEnum}. */
    ENUM,
    /** {@code flags} -- maps to {@code java.util.Set<String>}. */
    FLAGS,
    /** A resource handle ({@code own<T>} or {@code borrow<T>}) -- maps to {@code component.WitResource}. */
    RESOURCE;

    /**
     * Maps the tag string the native {@code wit-parser} binding passes across
     * the JNI boundary to the corresponding constant.
     *
     * @param tag The tag (e.g. {@code "u32"}, {@code "record"}, {@code "resource"}).
     * @return The matching {@link WitTypeKind}.
     * @throws IllegalArgumentException if the tag isn't a supported/known shape.
     */
    static WitTypeKind fromTag(String tag) {
        return switch (tag) {
            case "bool" -> BOOL;
            case "s8" -> S8;
            case "u8" -> U8;
            case "s16" -> S16;
            case "u16" -> U16;
            case "s32" -> S32;
            case "u32" -> U32;
            case "s64" -> S64;
            case "u64" -> U64;
            case "f32" -> F32;
            case "f64" -> F64;
            case "char" -> CHAR;
            case "string" -> STRING;
            case "list-u8" -> LIST_U8;
            case "list" -> LIST;
            case "option" -> OPTION;
            case "result" -> RESULT;
            case "tuple" -> TUPLE;
            case "record" -> RECORD;
            case "variant" -> VARIANT;
            case "enum" -> ENUM;
            case "flags" -> FLAGS;
            case "resource" -> RESOURCE;
            default -> throw new IllegalArgumentException("Unsupported WIT type shape: " + tag);
        };
    }
}
