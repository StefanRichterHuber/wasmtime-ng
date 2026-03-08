use crate::wasminstance::InstanceHandle;
use crate::wasmstore::StoreHandle;
use jni::{bind_java_type, jni_str, strings::JNIString};
use log::{debug, error};
use wasmtime::Extern;

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

        let export = instance.get_export(&mut *store, &name);
        match export {
            Some(Extern::Memory(mem)) => {
                debug!("Accessing single-instance memory {}", name);
                let data = mem.data_mut(store);
                let ptr = data.as_mut_ptr();
                let len = data.len();
                let buffer = unsafe { env.new_direct_byte_buffer(ptr, len)? };
                Ok(buffer)
            }
            Some(Extern::SharedMemory(mem)) => {
                debug!("Accessing shared memory {}", name);
                let ptr = mem.data().as_ptr();
                let len = mem.data_size();
                let buffer = unsafe { env.new_direct_byte_buffer(ptr as *mut u8, len)? };
                Ok(buffer)
            }
            _ => {
                error!("Wasm memory '{}' not found in instance!", name);
                let msg = format!("Wasm memory '{}' not found!", name);
                env.throw_new(jni_str!("java/lang/RuntimeException"), JNIString::from(msg))?;
                Ok(unsafe { env.new_direct_byte_buffer(std::ptr::null_mut(), 0)? })
            }
        }
    }

    fn get_memory_size<'local>(
        env: &mut ::jni::Env<'local>,
        _this: JWasmtimeMemory<'local>,
        instance: InstanceHandle,
        store: StoreHandle,
        name: ::jni::objects::JString<'local>,
    ) -> ::std::result::Result<::jni::sys::jlong, Self::Error> {
        let name = name.to_string();
        let instance = unsafe { instance.as_ref() };
        let store = unsafe { store.as_ref() };

        let export = instance.get_export(&mut *store, &name);
        match export {
            Some(Extern::Memory(mem)) => {
                debug!("Reading size of single-instance memory {}", name);
                Ok(mem.data_size(store) as ::jni::sys::jlong)
            }
            Some(Extern::SharedMemory(mem)) => {
                debug!("Reading size of shared memory {}", name);
                Ok(mem.data_size() as ::jni::sys::jlong)
            }
            _ => {
                let msg = format!("Wasm memory '{}' not found!", name);
                env.throw_new(jni_str!("java/lang/RuntimeException"), JNIString::from(msg))?;
                Ok(0)
            }
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

        let export = instance.get_export(&mut *store, &name);
        match export {
            Some(Extern::Memory(mem)) => {
                debug!("Growing single-instance memory {} by {} pages", name, delta);
                mem.grow(store, delta.try_into().unwrap()).unwrap();
                Ok(())
            }
            Some(Extern::SharedMemory(mem)) => {
                debug!("Growing shared memory {} by {} pages", name, delta);
                mem.grow(delta.try_into().unwrap()).unwrap();
                Ok(())
            }
            _ => {
                let msg = format!("Wasm memory '{}' not found!", name);
                env.throw_new(jni_str!("java/lang/RuntimeException"), JNIString::from(msg))?;
                Ok(())
            }
        }
    }
}
