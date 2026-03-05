use crate::wasmengine::EngineHandle;
use jni::{bind_java_type, sys::jlong};
use wasmtime::Module;

#[repr(transparent)]
#[derive(Copy, Clone)]
struct ModuleHandle(*const Module);

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
    rust_type = JWasmtimeModule,
    java_type = "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeModule",

    type_map = {
        unsafe EngineHandle => long,
        unsafe ModuleHandle => long
    },

    constructors {
        fn new(engine: EngineHandle),
    },

    native_methods {
        extern fn create_module(engine: EngineHandle, src: JByteBuffer ) -> jlong,
        extern fn close_module(module: ModuleHandle)
    }
}

impl JWasmtimeModuleNativeInterface for JWasmtimeModuleAPI {
    type Error = jni::errors::Error;

    fn close_module<'local>(
        _env: &mut ::jni::Env<'local>,
        _this: JWasmtimeModule<'local>,
        module: ModuleHandle,
    ) -> ::std::result::Result<(), Self::Error> {
        println!("Module closed");
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

        let script: &mut [u8] = unsafe { core::slice::from_raw_parts_mut(address, size) };

        // TODO convert wasmtime error to jni error
        let module = Module::new(unsafe { engine.as_ref() }, script).unwrap();
        let result = ModuleHandle::new(module);

        println!("Created module from source buffer");
        Ok(result.into())
    }
}
