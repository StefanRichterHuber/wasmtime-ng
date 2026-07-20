use jni::bind_java_type;
pub use jni::objects::JMap;

bind_java_type! {
    rust_type = pub JHashMap,
    java_type = "java.util.HashMap",

    constructors {
        fn new(),
        fn new_with_capacity(initial_capacity: jint),
        fn new_with_capacity_and_load_factor(initial_capacity: jint, load_factor: jfloat),
        fn new_from_map(m: JMap),
    },

    methods {
        fn clear(),
        fn contains_key( key: JObject) -> jboolean,
        fn contains_value(value: JObject) -> jboolean,
        fn get( key: JObject) -> JObject,
        fn get_or_default( key: JObject, default_value: JObject) -> JObject,
        fn is_empty() -> jboolean,
        fn put( key: JObject, value: JObject) -> JObject,
        fn put_all(m: JMap),
        fn put_if_absent( key: JObject, value: JObject) -> JObject,
        fn remove( key: JObject) -> JObject,
        fn remove_entry {
            name = "remove",
            sig = ( key: JObject, value: JObject) -> jboolean,
        },
        fn replace( key: JObject, value: JObject) -> JObject,
        fn replace_entry {
            name = "replace",
            sig = ( key: JObject, old_value: JObject, new_value: JObject) -> jboolean,
        },
        fn size() -> jint,
        fn values() -> JCollection,
        fn key_set() -> JSet,
        fn entry_set() -> JSet,
    },

    is_instance_of = {
        map: JMap,
    },
}

bind_java_type! {
    rust_type = pub JLinkedHashMap,
    java_type = "java.util.LinkedHashMap",

    type_map = {
        JHashMap => "java.util.HashMap"
    },

    constructors {
        fn new(),
        fn new_with_capacity(initial_capacity: jint),
        fn new_with_capacity_and_load_factor(initial_capacity: jint, load_factor: jfloat),
        fn new_from_map(m: JMap),
    },

    methods {
        fn clear(),
        fn contains_key( key: JObject) -> jboolean,
        fn contains_value(value: JObject) -> jboolean,
        fn get( key: JObject) -> JObject,
        fn get_or_default( key: JObject, default_value: JObject) -> JObject,
        fn is_empty() -> jboolean,
        fn put( key: JObject, value: JObject) -> JObject,
        fn put_all(m: JMap),
        fn put_if_absent( key: JObject, value: JObject) -> JObject,
        fn remove( key: JObject) -> JObject,
        fn remove_entry {
            name = "remove",
            sig = ( key: JObject, value: JObject) -> jboolean,
        },
        fn replace( key: JObject, value: JObject) -> JObject,
        fn replace_entry {
            name = "replace",
            sig = ( key: JObject, old_value: JObject, new_value: JObject) -> jboolean,
        },
        fn size() -> jint,
        fn values() -> JCollection,
        fn key_set() -> JSet,
        fn entry_set() -> JSet,
    },

    is_instance_of = {
        map: JMap,
        hash_map: JHashMap,
    },
}
