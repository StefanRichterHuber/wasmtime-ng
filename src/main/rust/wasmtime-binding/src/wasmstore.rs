use crate::wasmengine::EngineHandle;
use jni::{
    bind_java_type,
    objects::{JMap, JObject},
    refs::Global,
    sys::jlong,
};
use log::debug;
use wasmtime::Store;

///
/// StoreContent is a wrapper for the Java context map and the WasmtimeInstance (only populated when the instance is created)
///
pub struct StoreContent {
    pub context: Global<JMap<'static>>,
    pub instance: Option<Global<JObject<'static>>>,
}

impl StoreContent {
    pub fn new(context: Global<JMap<'static>>) -> Self {
        StoreContent {
            context,
            instance: None,
        }
    }
}

#[repr(transparent)]
#[derive(Copy, Clone)]
pub struct StoreHandle(*mut Store<StoreContent>);

impl StoreHandle {
    pub fn new(store: Store<StoreContent>) -> Self {
        let boxed = Box::new(store);
        StoreHandle(Box::into_raw(boxed))
    }

    pub unsafe fn as_ref(&self) -> &mut Store<StoreContent> {
        unsafe { &mut *self.0 }
    }

    pub unsafe fn into_box(self) -> Box<Store<StoreContent>> {
        unsafe { Box::from_raw(self.0 as *mut Store<StoreContent>) }
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
        extern fn create_store(engine: EngineHandle, context: JMap) -> jlong,
        extern fn close_store(store: StoreHandle)
    }
}

impl JWasmtimeStoreNativeInterface for JWasmtimeStoreAPI {
    type Error = jni::errors::Error;

    fn create_store<'local>(
        env: &mut ::jni::Env<'local>,
        _this: JWasmtimeStore<'local>,
        engine: EngineHandle,
        context: JMap,
    ) -> ::std::result::Result<::jni::sys::jlong, Self::Error> {
        let jmap = env.new_global_ref(context)?;

        let store = Store::new(unsafe { engine.as_ref() }, StoreContent::new(jmap));
        let result = StoreHandle::new(store);
        debug!("Created store with Map");
        Ok(result.into())
    }

    fn close_store<'local>(
        _env: &mut ::jni::Env<'local>,
        _this: JWasmtimeStore<'local>,
        store: StoreHandle,
    ) -> ::std::result::Result<(), Self::Error> {
        debug!("Store closed");
        drop(unsafe { store.into_box() });
        Ok(())
    }
}
