use jni::bind_java_type;

bind_java_type! {
    rust_type = pub JNumber,
    java_type = "java.lang.Number",

    methods {
        fn byte_value() -> jbyte,
        fn short_value() -> jshort,
        fn int_value() -> jint,
        fn long_value() -> jlong,
        fn float_value() -> jfloat,
        fn double_value() -> jdouble,
    }
}

bind_java_type! {
    rust_type = pub JByte,
    java_type = "java.lang.Byte",

    type_map = {
        JNumber =>  "java.lang.Number"
    },

    methods {
        static fn value_of(value: jbyte) -> JByte,
        static fn parse_byte(value: JString) -> jbyte,
        fn byte_value() -> jbyte,
        fn short_value() -> jshort,
        fn int_value() -> jint,
        fn long_value() -> jlong,
        fn float_value() -> jfloat,
        fn double_value() -> jdouble,
        fn compare_to(other: JByte) -> jint,
    },

    is_instance_of = {
        // With stem: generates as_number() method
        number: JNumber,
    },
}

bind_java_type! {
    rust_type = pub JShort,
    java_type = "java.lang.Short",

    type_map = {
        JNumber =>  "java.lang.Number"
    },

    methods {
        static fn value_of(value: jshort) -> JShort,
        static fn parse_short(value: JString) -> jshort,
        fn byte_value() -> jbyte,
        fn short_value() -> jshort,
        fn int_value() -> jint,
        fn long_value() -> jlong,
        fn float_value() -> jfloat,
        fn double_value() -> jdouble,
        fn compare_to(other: JShort) -> jint,
    },

    is_instance_of = {
        // With stem: generates as_number() method
        number: JNumber,
    },
}

bind_java_type! {
    rust_type = pub JInteger,
    java_type = "java.lang.Integer",

    type_map = {
        JNumber =>  "java.lang.Number"
    },
    methods {
        static fn value_of(value: jint) -> JInteger,
        static fn parse_int(value: JString) -> jint,
        fn byte_value() -> jbyte,
        fn short_value() -> jshort,
        fn int_value() -> jint,
        fn long_value() -> jlong,
        fn float_value() -> jfloat,
        fn double_value() -> jdouble,
        fn compare_to(other: JInteger) -> jint,
    },

    is_instance_of = {
        // With stem: generates as_number() method
        number: JNumber,
    },
}

bind_java_type! {
    rust_type = pub JLong,
    java_type = "java.lang.Long",

    type_map = {
        JNumber =>  "java.lang.Number"
    },

    methods {
        static fn value_of(value: jlong) -> JLong,
        static fn parse_long(value: JString) -> jlong,
        fn byte_value() -> jbyte,
        fn short_value() -> jshort,
        fn int_value() -> jint,
        fn long_value() -> jlong,
        fn float_value() -> jfloat,
        fn double_value() -> jdouble,
        fn compare_to(other: JLong) -> jint,
    },

    is_instance_of = {
        // With stem: generates as_number() method
        number: JNumber,
    },
}

bind_java_type! {
    rust_type = pub JFloat,
    java_type = "java.lang.Float",

    type_map = {
        JNumber =>  "java.lang.Number"
    },

    methods {
        static fn value_of(value: jfloat) -> JFloat,
        static fn parse_float(value: JString) -> jfloat,
        fn byte_value() -> jbyte,
        fn short_value() -> jshort,
        fn int_value() -> jint,
        fn long_value() -> jlong,
        fn float_value() -> jfloat,
        fn double_value() -> jdouble,
        fn compare_to(other: JFloat) -> jint,
    },

    is_instance_of = {
        // With stem: generates as_number() method
        number: JNumber,
    },
}

bind_java_type! {
    rust_type = pub JDouble,
    java_type = "java.lang.Double",

    type_map = {
        JNumber =>  "java.lang.Number"
    },

    methods {
        static fn value_of(value: jdouble) -> JDouble,
        static fn parse_double(value: JString) -> jdouble,
        fn byte_value() -> jbyte,
        fn short_value() -> jshort,
        fn int_value() -> jint,
        fn long_value() -> jlong,
        fn float_value() -> jfloat,
        fn double_value() -> jdouble,
        fn compare_to(other: JDouble) -> jint,
    },

    is_instance_of = {
        // With stem: generates as_number() method
        number: JNumber,
    },
}
