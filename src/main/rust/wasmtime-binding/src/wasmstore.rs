use crate::{
    wasmengine::EngineHandle, wasmengine::JWasmtimeEngine, wasminstance::JWasmtimeInstance,
};
use jni::{bind_java_type, objects::JMap, refs::Global, sys::jlong};
use log::debug;
use std::cell::RefCell;
use std::collections::HashMap;
use std::ffi::c_void;
use wasmtime::{AsContext, AsContextMut, Caller, Store, StoreContextMut};

thread_local! {
    /// Maps a `Store` raw pointer to a stack of active `Caller` pointers for this thread.
    static ACTIVE_CALLERS: RefCell<HashMap<usize, Vec<*mut c_void>>> = RefCell::new(HashMap::new());
}

/// A guard that registers a `Caller` in the thread-local storage for the duration of a JNI host call.
pub struct CallerGuard {
    store_ptr: usize,
}

impl CallerGuard {
    pub fn new<'a>(store_ptr: usize, caller: &mut Caller<'a, StoreContent>) -> Self {
        ACTIVE_CALLERS.with(|callers| {
            callers
                .borrow_mut()
                .entry(store_ptr)
                .or_default()
                .push(caller as *mut _ as *mut c_void);
        });
        Self { store_ptr }
    }
}

impl Drop for CallerGuard {
    fn drop(&mut self) {
        ACTIVE_CALLERS.with(|callers| {
            let mut map = callers.borrow_mut();
            if let Some(stack) = map.get_mut(&self.store_ptr) {
                stack.pop();
                if stack.is_empty() {
                    map.remove(&self.store_ptr);
                }
            }
        });
    }
}

///
/// StoreContent is a wrapper for the Java context map and the WasmtimeInstance (only populated when the instance is created)
///
pub struct StoreContent {
    pub context: Global<JMap<'static>>,
    pub instance: Option<Global<JWasmtimeInstance<'static>>>,
    pub store_content_ptr: Option<*mut Store<StoreContent>>,
}

impl StoreContent {
    pub fn new(context: Global<JMap<'static>>) -> Self {
        StoreContent {
            context,
            instance: None,
            store_content_ptr: None,
        }
    }
}

#[repr(transparent)]
#[derive(Copy, Clone)]
pub struct StoreHandle(*mut Store<StoreContent>);

impl StoreHandle {
    pub fn new(store: Store<StoreContent>) -> Self {
        let boxed = Box::new(store);
        let handle = StoreHandle(Box::into_raw(boxed));
        unsafe { handle.as_ref().data_mut().store_content_ptr = Some(handle.0) };
        handle
    }

    pub unsafe fn as_ref(&self) -> &mut Store<StoreContent> {
        unsafe { &mut *self.0 }
    }

    pub unsafe fn into_box(self) -> Box<Store<StoreContent>> {
        unsafe { Box::from_raw(self.0 as *mut Store<StoreContent>) }
    }
}

impl AsContext for StoreHandle {
    type Data = StoreContent;

    fn as_context(&self) -> wasmtime::StoreContext<'_, Self::Data> {
        let store_ptr = self.0 as usize;
        let active_caller = ACTIVE_CALLERS.with(|callers| {
            callers
                .borrow()
                .get(&store_ptr)
                .and_then(|stack| stack.last().copied())
        });

        if let Some(caller_ptr) = active_caller {
            // We are inside a host call for THIS store! Safely re-borrow the Caller.
            let caller = unsafe { &*(caller_ptr as *const Caller<'_, StoreContent>) };
            caller.as_context()
        } else {
            // Not in a host call, use the Store pointer directly.
            unsafe { (&*self.0).into() }
        }
    }
}

impl AsContextMut for StoreHandle {
    fn as_context_mut(&mut self) -> StoreContextMut<'_, StoreContent> {
        let store_ptr = self.0 as usize;
        let active_caller = ACTIVE_CALLERS.with(|callers| {
            callers
                .borrow()
                .get(&store_ptr)
                .and_then(|stack| stack.last().copied())
        });

        if let Some(caller_ptr) = active_caller {
            // We are inside a host call for THIS store! Safely re-borrow the Caller.
            let caller = unsafe { &mut *(caller_ptr as *mut Caller<'_, StoreContent>) };
            caller.as_context_mut()
        } else {
            // Not in a host call, use the Store pointer directly.
            unsafe { (&mut *self.0).into() }
        }
    }
}

impl From<*mut Store<StoreContent>> for StoreHandle {
    fn from(value: *mut Store<StoreContent>) -> Self {
        StoreHandle(value)
    }
}

impl From<StoreHandle> for jlong {
    fn from(handle: StoreHandle) -> Self {
        handle.0 as jlong
    }
}

bind_java_type! {
    rust_type = pub JWasmtimeStore,
    java_type = "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeStore",

    type_map = {
        unsafe EngineHandle => long,
        unsafe StoreHandle => long,
        JWasmtimeEngine => "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeEngine",
    },

    fields {
        store_ptr: jlong,
        engine: JWasmtimeEngine,
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
