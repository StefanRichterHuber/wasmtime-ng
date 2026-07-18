use jni::bind_java_type;
pub use jni::objects::{JCollection, JList, JSet};


bind_java_type! {
    rust_type = pub JArrayList,
    java_type = "java.util.ArrayList",

    constructors {
        fn new(),
        fn new_with_capacity(initial_capacity: jint),
        fn new_from_collection(c: JCollection),
    },

    methods {
        fn add(element: JObject) -> jboolean,
        fn add_at {
            name = "add",
            sig = (index: jint, element: JObject) -> void,
        },
        fn add_all(c: JCollection) -> jboolean,
        fn add_all_at {
            name = "addAll",
            sig = (index: jint, c: JCollection) -> jboolean,
        },
        fn clear(),
        fn contains(o: JObject) -> jboolean,
        fn get(index: jint) -> JObject,
        fn index_of(o: JObject) -> jint,
        fn is_empty() -> jboolean,
        fn last_index_of(o: JObject) -> jint,
        fn remove_at {
            name = "remove",
            sig = (index: jint) -> JObject,
        },
        fn remove(o: JObject) -> jboolean,
        fn retain_all(c: JCollection) -> jboolean,
        fn set(index: jint, element: JObject) -> JObject,
        fn size() -> jint,
        fn to_array() -> JObject[],
        fn trim_to_size(),
    },

    is_instance_of = {
        collection: JCollection,
        list: JList,
    },
}

bind_java_type! {
    rust_type = pub JHashSet,
    java_type = "java.util.HashSet",

    constructors {
        fn new(),
        fn new_with_capacity(initial_capacity: jint),
        fn new_with_capacity_and_load_factor(initial_capacity: jint, load_factor: jfloat),
        fn new_from_collection(c: JCollection),
    },

    methods {
        fn add(element: JObject) -> jboolean,
        fn clear(),
        fn contains(o: JObject) -> jboolean,
        fn is_empty() -> jboolean,
        fn remove(o: JObject) -> jboolean,
        fn size() -> jint,
        fn to_array() -> JObject[],
    },

    is_instance_of = {
        collection: JCollection,
        set: JSet,
    },
}

bind_java_type! {
    rust_type = pub JLinkedHashSet,
    java_type = "java.util.LinkedHashSet",

    type_map = {
        JHashSet => "java.util.HashSet"
    },

    constructors {
        fn new(),
        fn new_with_capacity(initial_capacity: jint),
        fn new_with_capacity_and_load_factor(initial_capacity: jint, load_factor: jfloat),
        fn new_from_collection(c: JCollection),
    },

    methods {
        fn add(element: JObject) -> jboolean,
        fn clear(),
        fn contains(o: JObject) -> jboolean,
        fn is_empty() -> jboolean,
        fn remove(o: JObject) -> jboolean,
        fn size() -> jint,
        fn to_array() -> JObject[],
    },

    is_instance_of = {
        collection: JCollection,
        hash_set: JHashSet,
        set: JSet,
    },
}
