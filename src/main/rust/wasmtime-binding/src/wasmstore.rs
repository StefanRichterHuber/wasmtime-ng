use crate::wasmengine::EngineHandle;
use jni::{bind_java_type, jni_sig, jni_str, objects::JMap, refs::Global, sys::jlong};
use wasmtime::Store;

#[repr(transparent)]
#[derive(Copy, Clone)]
pub struct StoreHandle(*mut Store<Global<JMap<'static>>>);

impl StoreHandle {
    pub fn new(store: Store<Global<JMap<'static>>>) -> Self {
        let boxed = Box::new(store);
        StoreHandle(Box::into_raw(boxed))
    }

    pub unsafe fn as_ref(&self) -> &mut Store<Global<JMap<'static>>> {
        unsafe { &mut *self.0 }
    }

    pub unsafe fn into_box(self) -> Box<Store<Global<JMap<'static>>>> {
        unsafe { Box::from_raw(self.0 as *mut Store<Global<JMap<'static>>>) }
    }
}

impl From<StoreHandle> for jlong {
    fn from(handle: StoreHandle) -> Self {
        handle.0 as jlong
    }
}

bind_java_type! {
    rust_type = JWasmtimeStore,
    java_type = "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeStore",

    type_map = {
        unsafe EngineHandle => long,
        unsafe StoreHandle => long,
    },

    constructors {
        fn new(engine: EngineHandle, src: JByteBuffer),
    },

    native_methods {
        extern fn create_store(engine: EngineHandle) -> jlong,
        extern fn close_store(store: StoreHandle)
    }
}

impl JWasmtimeStoreNativeInterface for JWasmtimeStoreAPI {
    type Error = jni::errors::Error;

    fn create_store<'local>(
        env: &mut ::jni::Env<'local>,
        _this: JWasmtimeStore<'local>,
        engine: EngineHandle,
    ) -> ::std::result::Result<::jni::sys::jlong, Self::Error> {
        let map_class = env.find_class(jni_str!("java/util/HashMap"))?;
        let map_obj = env.new_object(map_class, &jni_sig!(() -> void), &[])?;

        let jmap = JMap::cast_local(env, map_obj)?;
        let jmap = env.new_global_ref(jmap)?;

        let store = Store::new(unsafe { engine.as_ref() }, jmap);
        let result = StoreHandle::new(store);
        println!("Created store with Map");
        Ok(result.into())
    }

    fn close_store<'local>(
        _env: &mut ::jni::Env<'local>,
        _this: JWasmtimeStore<'local>,
        store: StoreHandle,
    ) -> ::std::result::Result<(), Self::Error> {
        println!("Store closed");
        drop(unsafe { store.into_box() });
        Ok(())
    }
}
