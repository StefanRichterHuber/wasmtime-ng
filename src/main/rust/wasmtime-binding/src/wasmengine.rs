use jni::{bind_java_type, objects::{JClass, JPrimitiveArray}, sys::jlong};
use log::debug;
use wasmtime::Engine;

use crate::wasmexception::handle_wasmtime_error;

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
     rust_type = pub JWasmtimeEngine,
     java_type = "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeEngine",

    type_map = {
        unsafe EngineHandle => long,
    },

    fields = {
        engine_ptr: jlong,
    },

    constructors {
        fn new(),
    },

    methods {
        static fn runtime_log(level: jint, message: JString),
    },

    native_methods {
        extern static fn close_engine(handle: EngineHandle),
        extern fn create_engine() -> jlong,
        extern fn precompile(handle: EngineHandle, src: JByteBuffer) -> jbyte[],
        extern static fn init_logging(level: jint)
    },
}

impl JWasmtimeEngineNativeInterface for JWasmtimeEngineAPI {
    type Error = jni::errors::Error;

    fn close_engine<'local>(
        _env: &mut ::jni::Env<'local>,
        _clazz: JClass<'local>,
        handle: EngineHandle,
    ) -> ::std::result::Result<(), Self::Error> {
        debug!("Engine closed");
        drop(unsafe { handle.into_box() });
        Ok(())
    }

    fn create_engine<'local>(
        _env: &mut ::jni::Env<'local>,
        _this: JWasmtimeEngine<'local>,
    ) -> ::std::result::Result<::jni::sys::jlong, Self::Error> {
        debug!("Engine created");
        let mut config = wasmtime::Config::new();
        config.strategy(wasmtime::Strategy::Cranelift);
        config.wasm_threads(true);
        config.wasm_bulk_memory(true);
        config.shared_memory(true);

        match Engine::new(&config) {
            Ok(engine) => {
                let result = EngineHandle::new(engine);
                Ok(result.into())
            }
            Err(e) => {
                handle_wasmtime_error(_env, e)?;
                Err(jni::errors::Error::JavaException)
            }
        }
    }

    fn init_logging<'local>(
        env: &mut ::jni::Env<'local>,
        _class: ::jni::objects::JClass<'local>,
        level: ::jni::sys::jint,
    ) -> ::std::result::Result<(), Self::Error> {
        crate::logging::init_logging(env, level)
    }

    fn precompile<'local>(
        env: &mut ::jni::Env<'local>,
        _this: JWasmtimeEngine<'local>,
        handle: EngineHandle,
        src: ::jni::objects::JByteBuffer<'local>,
    ) -> ::std::result::Result<
        ::jni::objects::JPrimitiveArray<'local, ::jni::sys::jbyte>,
        Self::Error,
    > {
        let engine = unsafe { handle.as_ref() };
        // Read directly the direct byte buffer
        let address = env.get_direct_buffer_address(&src)?;
        let size = env.get_direct_buffer_capacity(&src)?;
        let script: &[u8] = unsafe { core::slice::from_raw_parts(address, size) };
        match engine.precompile_module(script) {
            Ok(result) => {
                let result_array = env.new_byte_array(result.len())?;
                let result: &[i8] = unsafe {
                    std::slice::from_raw_parts(result.as_ptr() as *const i8, result.len())
                };
                result_array.set_region(env, 0, &result)?;
                Ok(result_array)
            }
            Err(e) => {
                handle_wasmtime_error(env, e)?;
                Ok(JPrimitiveArray::null())
            }
        }
    }
}
