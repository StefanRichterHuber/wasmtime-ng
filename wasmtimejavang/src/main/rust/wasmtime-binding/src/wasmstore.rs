use crate::{wasmengine::EngineHandle, wasmengine::JWasmtimeEngine};
use jni::{
    bind_java_type,
    objects::{JClass, JMap},
    refs::Global,
    sys::jlong,
};
use log::debug;
use std::cell::RefCell;
use std::collections::HashMap;
use std::ffi::c_void;
use wasmtime::{AsContext, AsContextMut, Caller, Store, StoreContextMut};

/// A raw pointer to whatever borrow of the `Store` is currently active on this
/// thread for re-entrancy purposes: either a core wasm `Caller` (defined via
/// `Linker::define`) or a component `StoreContextMut` (defined via
/// `component::LinkerInstance::func_new`). Both support `.as_context{,_mut}()`.
#[derive(Copy, Clone)]
enum ActiveCallerPtr {
    Caller(*mut c_void),
    StoreContext(*mut c_void),
}

thread_local! {
    /// Maps a `Store` raw pointer to a stack of active caller pointers for this thread.
    static ACTIVE_CALLERS: RefCell<HashMap<usize, Vec<ActiveCallerPtr>>> = RefCell::new(HashMap::new());
}

/// A guard that registers a `Caller`/`StoreContextMut` in the thread-local storage for the duration of a JNI host call.
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
                .push(ActiveCallerPtr::Caller(caller as *mut _ as *mut c_void));
        });
        Self { store_ptr }
    }

    /// Same as `new`, but for a component-model host call, which is handed a
    /// `StoreContextMut` instead of a `Caller`.
    pub fn new_from_store_context<'a>(
        store_ptr: usize,
        ctx: &mut StoreContextMut<'a, StoreContent>,
    ) -> Self {
        ACTIVE_CALLERS.with(|callers| {
            callers
                .borrow_mut()
                .entry(store_ptr)
                .or_default()
                .push(ActiveCallerPtr::StoreContext(
                    ctx as *mut _ as *mut c_void,
                ));
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
    pub store_content_ptr: Option<*mut Store<StoreContent>>,
}

impl StoreContent {
    pub fn new(context: Global<JMap<'static>>) -> Self {
        StoreContent {
            context,
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
        let mut handle = StoreHandle(Box::into_raw(boxed));
        unsafe { handle.as_ref().data_mut().store_content_ptr = Some(handle.0) };
        handle
    }

    /// Returns a mutable reference to the underlying `Store`.
    ///
    /// # Safety
    /// The caller must ensure that the underlying raw pointer is valid and that the `Store` it points to has not been dropped.
    /// As this returns a mutable reference, the caller must also ensure that no other references (mutable or immutable) to the same `Store` exist simultaneously to avoid aliasing violations.
    pub unsafe fn as_ref(&mut self) -> &mut Store<StoreContent> {
        unsafe { &mut *self.0 }
    }

    /// Converts the raw pointer back into a `Box<Store<StoreContent>>`.
    ///
    /// # Safety
    /// The caller must ensure that the underlying raw pointer is valid and has not already been freed.
    /// Calling this method transfers ownership to the returned `Box`, so the raw pointer must not be used afterwards to avoid double-free or use-after-free errors.
    pub unsafe fn into_box(self) -> Box<Store<StoreContent>> {
        unsafe { Box::from_raw(self.0) }
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

        match active_caller {
            Some(ActiveCallerPtr::Caller(caller_ptr)) => {
                // We are inside a core wasm host call for THIS store! Safely re-borrow the Caller.
                let caller = unsafe { &*(caller_ptr as *const Caller<'_, StoreContent>) };
                caller.as_context()
            }
            Some(ActiveCallerPtr::StoreContext(ctx_ptr)) => {
                // We are inside a component host call for THIS store! Safely re-borrow the StoreContextMut.
                let ctx = unsafe { &*(ctx_ptr as *const StoreContextMut<'_, StoreContent>) };
                ctx.as_context()
            }
            None => {
                // Not in a host call, use the Store pointer directly.
                unsafe { (&*self.0).into() }
            }
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

        match active_caller {
            Some(ActiveCallerPtr::Caller(caller_ptr)) => {
                // We are inside a core wasm host call for THIS store! Safely re-borrow the Caller.
                let caller = unsafe { &mut *(caller_ptr as *mut Caller<'_, StoreContent>) };
                caller.as_context_mut()
            }
            Some(ActiveCallerPtr::StoreContext(ctx_ptr)) => {
                // We are inside a component host call for THIS store! Safely re-borrow the StoreContextMut.
                let ctx = unsafe { &mut *(ctx_ptr as *mut StoreContextMut<'_, StoreContent>) };
                ctx.as_context_mut()
            }
            None => {
                // Not in a host call, use the Store pointer directly.
                unsafe { (&mut *self.0).into() }
            }
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
        extern static fn close_store(store: StoreHandle)
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
        _class: JClass<'local>,
        store: StoreHandle,
    ) -> ::std::result::Result<(), Self::Error> {
        debug!("Store closed");
        drop(unsafe { store.into_box() });
        Ok(())
    }
}
