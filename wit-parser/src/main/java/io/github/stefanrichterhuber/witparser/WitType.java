package io.github.stefanrichterhuber.witparser;

/**
 * The WIT primitive types this MVP of the WIT-to-Java codegen pipeline
 * understands. Complex types (records, variants, enums, flags, lists,
 * options, results, resources, and named type aliases) are not supported
 * yet -- {@link WitParser} throws if it encounters one.
 */
public enum WitType {
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
    STRING;

    /**
     * Maps a WIT type keyword (e.g. {@code "u32"}, as it appears in a
     * {@code .wit} file) to the corresponding constant.
     *
     * @param name The WIT type keyword.
     * @return The matching {@link WitType}.
     * @throws IllegalArgumentException if the keyword is not a supported
     *                                   primitive WIT type.
     */
    public static WitType fromWit(String name) {
        return switch (name) {
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
            default -> throw new IllegalArgumentException("Unsupported WIT type: " + name);
        };
    }
}
