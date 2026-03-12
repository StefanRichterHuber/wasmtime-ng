use crate::wasminstance::JWasmtimeInstance;
use jni::bind_java_type;

bind_java_type! {
    rust_type = pub JWasmtimeFunction,
    java_type = "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeFunction",

    type_map = {
        JWasmtimeInstance => "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeInstance",
    },

    methods = {
        fn call(instance: JWasmtimeInstance, context: JMap, args: JObject[]) -> JObject[],
    }
}
