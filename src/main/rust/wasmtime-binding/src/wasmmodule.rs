use crate::{
    wasmengine::{EngineHandle, JWasmtimeEngine},
    wasmexception::handle_wasmtime_error,
};
use jni::{bind_java_type, objects::JClass, sys::jlong};
use log::debug;
use wasmtime::Module;

#[repr(transparent)]
#[derive(Copy, Clone)]
pub struct ModuleHandle(*const Module);

#[allow(dead_code)]
impl ModuleHandle {
    pub fn new(module: Module) -> Self {
        let boxed = Box::new(module);
        ModuleHandle(Box::into_raw(boxed))
    }

    pub unsafe fn as_ref(&self) -> &Module {
        unsafe { &*self.0 }
    }

    pub unsafe fn into_box(self) -> Box<Module> {
        unsafe { Box::from_raw(self.0 as *mut Module) }
    }
}

impl From<ModuleHandle> for jlong {
    fn from(handle: ModuleHandle) -> Self {
        handle.0 as jlong
    }
}

bind_java_type! {
    rust_type = pub JWasmtimeModule,
    java_type = "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeModule",

    type_map = {
        unsafe EngineHandle => long,
        unsafe ModuleHandle => long,
        JWasmtimeEngine => "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeEngine"
    },

    fields = {
        module_ptr: jlong,
        engine: JWasmtimeEngine,
    },

    constructors {
        fn new_from_bytes(engine: JWasmtimeEngine, source: jbyte[]),
        fn new_from_input_string(engine: JWasmtimeEngine, wat: JString),

    },

    native_methods {
        extern fn create_module(engine: EngineHandle, src: JByteBuffer ) -> jlong,
        extern fn create_module_from_precompiled(engine: EngineHandle, src: JByteBuffer ) -> jlong,
        extern static fn close_module(module: ModuleHandle)
    }
}

impl JWasmtimeModuleNativeInterface for JWasmtimeModuleAPI {
    type Error = jni::errors::Error;

    fn close_module<'local>(
        _env: &mut ::jni::Env<'local>,
        _class: JClass<'local>,
        module: ModuleHandle,
    ) -> ::std::result::Result<(), Self::Error> {
        debug!("Module closed");
        drop(unsafe { module.into_box() });
        Ok(())
    }

    fn create_module<'local>(
        env: &mut ::jni::Env<'local>,
        _this: JWasmtimeModule<'local>,
        engine: EngineHandle,
        src: ::jni::objects::JByteBuffer<'local>,
    ) -> ::std::result::Result<::jni::sys::jlong, Self::Error> {
        let address = env.get_direct_buffer_address(&src)?;
        let size = env.get_direct_buffer_capacity(&src)?;

        let script: &[u8] = unsafe { core::slice::from_raw_parts(address, size) };

        let module = match Module::new(unsafe { engine.as_ref() }, script) {
            Ok(module) => ModuleHandle::new(module).into(),
            Err(e) => {
                handle_wasmtime_error(env, e)?;
                0
            }
        };

        debug!("Created module from source buffer");
        Ok(module)
    }

    fn create_module_from_precompiled<'local>(
        env: &mut ::jni::Env<'local>,
        _this: JWasmtimeModule<'local>,
        engine: EngineHandle,
        src: ::jni::objects::JByteBuffer<'local>,
    ) -> ::std::result::Result<::jni::sys::jlong, Self::Error> {
        let address = env.get_direct_buffer_address(&src)?;
        let size = env.get_direct_buffer_capacity(&src)?;

        let bytes: &[u8] = unsafe { core::slice::from_raw_parts(address, size) };

        let module = match unsafe { Module::deserialize(engine.as_ref(), bytes) } {
            Ok(module) => ModuleHandle::new(module).into(),
            Err(error) => {
                handle_wasmtime_error(env, error)?;
                0
            }
        };
        debug!("Created module from precompiled wasm code");
        Ok(module)
    }
}
