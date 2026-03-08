use crate::wasmengine::EngineHandle;
use jni::{bind_java_type, sys::jlong};
use log::debug;
use wasmtime::{MemoryType, SharedMemory};

#[repr(transparent)]
#[derive(Copy, Clone)]
pub struct SharedMemoryHandle(*const SharedMemory);

#[allow(dead_code)]
impl SharedMemoryHandle {
    pub fn new(shared_memory: SharedMemory) -> Self {
        let boxed = Box::new(shared_memory);
        SharedMemoryHandle(Box::into_raw(boxed))
    }

    pub unsafe fn as_ref(&self) -> &SharedMemory {
        unsafe { &*self.0 }
    }

    pub unsafe fn into_box(self) -> Box<SharedMemory> {
        unsafe { Box::from_raw(self.0 as *mut SharedMemory) }
    }
}

impl From<SharedMemoryHandle> for jlong {
    fn from(handle: SharedMemoryHandle) -> Self {
        handle.0 as jlong
    }
}

bind_java_type! {
    rust_type = JWasmtimeSharedMemory,
    java_type = "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeSharedMemory",

    type_map = {
        unsafe EngineHandle => long,
        unsafe SharedMemoryHandle => long
    },

    constructors {
        fn new(engine: EngineHandle, initial_pages: long, max_pages: long),
    },

    native_methods {
        extern fn create_shared_memory(engine: EngineHandle, initial_pages: jlong, max_pages: jlong) -> jlong,
        extern fn close_shared_memory(shared_memory: SharedMemoryHandle),
        extern fn get_direct_buffer(shared_memory: SharedMemoryHandle) -> JByteBuffer,
        extern fn get_memory_size(shared_memory: SharedMemoryHandle) -> jlong,
        extern fn grow_memory(shared_memory: SharedMemoryHandle, delta: jlong),
    }
}

impl JWasmtimeSharedMemoryNativeInterface for JWasmtimeSharedMemoryAPI {
    type Error = jni::errors::Error;

    fn create_shared_memory<'local>(
        _env: &mut ::jni::Env<'local>,
        _this: JWasmtimeSharedMemory<'local>,
        engine: EngineHandle,
        initial_pages: ::jni::sys::jlong,
        max_pages: ::jni::sys::jlong,
    ) -> ::std::result::Result<::jni::sys::jlong, Self::Error> {
        let engine = unsafe { engine.as_ref() };
        let ty = MemoryType::shared(initial_pages as u32, max_pages as u32);
        let shared_memory = SharedMemory::new(engine, ty).unwrap();

        let result = SharedMemoryHandle::new(shared_memory);
        debug!("Created SharedMemory");
        Ok(result.into())
    }

    fn close_shared_memory<'local>(
        _env: &mut ::jni::Env<'local>,
        _this: JWasmtimeSharedMemory<'local>,
        shared_memory: SharedMemoryHandle,
    ) -> ::std::result::Result<(), Self::Error> {
        debug!("SharedMemory closed");
        drop(unsafe { shared_memory.into_box() });
        Ok(())
    }

    fn get_direct_buffer<'local>(
        env: &mut ::jni::Env<'local>,
        _this: JWasmtimeSharedMemory<'local>,
        shared_memory: SharedMemoryHandle,
    ) -> ::std::result::Result<::jni::objects::JByteBuffer<'local>, Self::Error> {
        let shared_memory = unsafe { shared_memory.as_ref() };
        let data = shared_memory.data();
        let ptr = data.as_ptr();
        let len = data.len();
        let buffer = unsafe { env.new_direct_byte_buffer(ptr as *mut u8, len)? };
        Ok(buffer)
    }

    fn get_memory_size<'local>(
        _env: &mut ::jni::Env<'local>,
        _this: JWasmtimeSharedMemory<'local>,
        shared_memory: SharedMemoryHandle,
    ) -> ::std::result::Result<::jni::sys::jlong, Self::Error> {
        let shared_memory = unsafe { shared_memory.as_ref() };
        Ok(shared_memory.data_size() as ::jni::sys::jlong)
    }

    fn grow_memory<'local>(
        _env: &mut ::jni::Env<'local>,
        _this: JWasmtimeSharedMemory<'local>,
        shared_memory: SharedMemoryHandle,
        delta: ::jni::sys::jlong,
    ) -> ::std::result::Result<(), Self::Error> {
        let shared_memory = unsafe { shared_memory.as_ref() };
        shared_memory.grow(delta as u64).unwrap();
        Ok(())
    }
}
