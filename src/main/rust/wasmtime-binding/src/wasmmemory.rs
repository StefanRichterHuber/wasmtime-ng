use crate::wasminstance::InstanceHandle;
use crate::wasmstore::StoreHandle;
use jni::{bind_java_type, jni_str, strings::JNIString};

bind_java_type! {
    rust_type = JWasmtimeMemory,
    java_type = "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeMemory",

    type_map = {
        unsafe StoreHandle => long,
        unsafe InstanceHandle => long,
    },

    constructors {
        fn new(instance: InstanceHandle, name: JString)
    },

    methods {

    },

    native_methods {
        extern fn grow_memory(instance: InstanceHandle, store: StoreHandle,  name: JString, delta: jlong),
        extern fn get_direct_buffer(instance: InstanceHandle, store: StoreHandle, name: JString) -> JByteBuffer,
        extern fn get_memory_size(instance: InstanceHandle, store: StoreHandle, name: JString) -> jlong,
    }
}

impl JWasmtimeMemoryNativeInterface for JWasmtimeMemoryAPI {
    type Error = jni::errors::Error;

    fn get_direct_buffer<'local>(
        env: &mut ::jni::Env<'local>,
        _this: JWasmtimeMemory<'local>,
        instance: InstanceHandle,
        store: StoreHandle,
        name: ::jni::objects::JString<'local>,
    ) -> ::std::result::Result<::jni::objects::JByteBuffer<'local>, Self::Error> {
        let name = name.to_string();
        let instance = unsafe { instance.as_ref() };
        let store = unsafe { store.as_ref() };
        match instance.get_memory(&mut *store, &name) {
            Some(mem) => {
                let data = mem.data_mut(store);
                let ptr = data.as_mut_ptr();
                let len = data.len();
                let buffer = unsafe { env.new_direct_byte_buffer(ptr, len)? };
                Ok(buffer)
            }
            None => {
                let msg = format!("Wasm memory '{}' not found!", name);
                env.throw_new(jni_str!("java/lang/RuntimeException"), JNIString::from(msg))?;
                Ok(unsafe { env.new_direct_byte_buffer(std::ptr::null_mut(), 0)? })
            }
        }
    }

    fn get_memory_size<'local>(
        _env: &mut ::jni::Env<'local>,
        _this: JWasmtimeMemory<'local>,
        instance: InstanceHandle,
        store: StoreHandle,
        name: ::jni::objects::JString<'local>,
    ) -> ::std::result::Result<::jni::sys::jlong, Self::Error> {
        let name = name.to_string();
        let instance = unsafe { instance.as_ref() };
        let store = unsafe { store.as_ref() };
        match instance.get_memory(&mut *store, &name) {
            Some(mem) => Ok(mem.data_size(store) as ::jni::sys::jlong),
            None => Ok(0),
        }
    }

    fn grow_memory<'local>(
        env: &mut ::jni::Env<'local>,
        _this: JWasmtimeMemory<'local>,
        instance: InstanceHandle,
        store: StoreHandle,
        name: ::jni::objects::JString<'local>,
        delta: ::jni::sys::jlong,
    ) -> ::std::result::Result<(), Self::Error> {
        let name = name.to_string();
        let instance = unsafe { instance.as_ref() };
        let store = unsafe { store.as_ref() };
        match instance.get_memory(&mut *store, &name) {
            Some(mem) => {
                mem.grow(store, delta.try_into().unwrap()).unwrap();
                Ok(())
            }
            None => {
                let msg = format!("Wasm memory '{}' not found!", name);
                env.throw_new(jni_str!("java/lang/RuntimeException"), JNIString::from(msg))?;
                Ok(())
            }
        }
    }
}
