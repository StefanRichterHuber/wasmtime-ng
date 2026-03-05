use jni::{bind_java_type, sys::jlong};
use wasmtime::Engine;

#[repr(transparent)]
#[derive(Copy, Clone)]
pub struct EngineHandle(*const Engine);

impl EngineHandle {
    pub fn new(engine: Engine) -> Self {
        let boxed = Box::new(engine);
        EngineHandle(Box::into_raw(boxed))
    }

    pub unsafe fn as_ref(&self) -> &Engine {
        unsafe { &*self.0 }
    }

    pub unsafe fn into_box(self) -> Box<Engine> {
        unsafe { Box::from_raw(self.0 as *mut Engine) }
    }
}

impl From<EngineHandle> for jlong {
    fn from(handle: EngineHandle) -> Self {
        handle.0 as jlong
    }
}

bind_java_type! {
     rust_type = JWasmtimeEngine,
     java_type = "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeEngine",

    type_map = {
        unsafe EngineHandle => long,
    },

    constructors {
        fn new(),
    },

    native_methods {
        extern fn close_engine(handle: EngineHandle),
        extern fn create_engine() -> jlong
    },
}

impl JWasmtimeEngineNativeInterface for JWasmtimeEngineAPI {
    type Error = jni::errors::Error;

    fn close_engine<'local>(
        _env: &mut ::jni::Env<'local>,
        _this: JWasmtimeEngine<'local>,
        handle: EngineHandle,
    ) -> ::std::result::Result<(), Self::Error> {
        println!("Engine closed");
        drop(unsafe { handle.into_box() });
        Ok(())
    }

    fn create_engine<'local>(
        _env: &mut ::jni::Env<'local>,
        _this: JWasmtimeEngine<'local>,
    ) -> ::std::result::Result<::jni::sys::jlong, Self::Error> {
        println!("Engine created");
        let engine = Engine::default();
        let result = EngineHandle::new(engine);
        return Ok(result.into());
    }
}
