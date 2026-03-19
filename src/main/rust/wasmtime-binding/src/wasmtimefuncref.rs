use crate::wasmexception::handle_wasmtime_error;
use crate::wasminstance::with_instance;
use crate::wasmtimefunction::JWasmtimeFunction;
use crate::{wasminstance::JWasmtimeInstance, wasmstore::StoreContent};
use jni::{bind_java_type, sys::jlong};
use log::debug;
use wasmtime::{AsContext, Func, Val};

use crate::{
    wasminstance::{
        convert_java_array_to_val_vector, convert_val_vector_to_java_array, empty_array,
    },
    wasmstore::StoreHandle,
};

pub struct FuncHandle(*const Func);

#[allow(dead_code)]
impl FuncHandle {
    pub fn new(func: Func) -> Self {
        let boxed = Box::new(func);
        FuncHandle(Box::into_raw(boxed))
    }

    pub unsafe fn as_ref(&self) -> &Func {
        unsafe { &*self.0 }
    }

    pub unsafe fn into_box(self) -> Box<Func> {
        unsafe { Box::from_raw(self.0 as *mut Func) }
    }
}

impl From<FuncHandle> for jlong {
    fn from(handle: FuncHandle) -> Self {
        handle.0 as jlong
    }
}

bind_java_type! {
    rust_type = pub JWasmtimeFuncRef,
    java_type = "io.github.stefanrichterhuber.wasmtimejavang.internal.WasmtimeFuncRef",

    type_map = {
        unsafe FuncHandle => long,
        unsafe StoreHandle => long,
        JWasmtimeInstance => "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeInstance",
        JWasmtimeFunction => "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeFunction",
    },

    constructors {
        fn new(func: FuncHandle, store: StoreHandle, instance: JWasmtimeInstance),
    },

    methods = {
        fn call(instance: JWasmtimeInstance, args: JObject[]) -> JObject[],
    },

    is_instance_of = {
        // With stem: generates as_base() method
        base: JWasmtimeFunction,
    },

    native_methods {
        extern static fn close_native_func(func: FuncHandle),
        extern fn invoke_native_func(func: FuncHandle, store: StoreHandle, instance: JWasmtimeInstance, args: JObject[] ) -> JObject[]
    }
}

impl JWasmtimeFuncRefNativeInterface for JWasmtimeFuncRefAPI {
    type Error = jni::errors::Error;

    fn invoke_native_func<'local>(
        env: &mut ::jni::Env<'local>,
        _this: JWasmtimeFuncRef<'local>,
        func: FuncHandle,
        store: StoreHandle,
        instance: JWasmtimeInstance<'local>,
        args: ::jni::objects::JObjectArray<'local, ::jni::objects::JObject<'local>>,
    ) -> ::std::result::Result<::jni::objects::JObjectArray<'local>, Self::Error> {
        let instance = env.new_global_ref(instance)?;
        with_instance(env, Some(instance), |env, local_instance| {
            let func = unsafe { func.as_ref() };
            let param_types: Vec<wasmtime::ValType> = func.ty(store).params().collect();
            let result_types: Vec<wasmtime::ValType> = func.ty(store).results().collect();
            let args = convert_java_array_to_val_vector(env, store, args, &param_types)?;
            let result_len = result_types.len();
            let mut results = vec![Val::I64(0); result_len];

            let result = match func.call(store, &args, &mut results) {
                Ok(()) => {
                    debug!("Successfully called wrapped function");
                    convert_val_vector_to_java_array(env, &store, local_instance, &results)?
                }
                Err(e) => {
                    handle_wasmtime_error(env, e)?;
                    empty_array(env, result_len.try_into().unwrap())?
                }
            };

            Ok(result)
        })
    }

    fn close_native_func<'local>(
        _env: &mut ::jni::Env<'local>,
        _class: ::jni::objects::JClass<'local>,
        func: FuncHandle,
    ) -> ::std::result::Result<(), Self::Error> {
        debug!("Function reference closed");
        drop(unsafe { func.into_box() });
        Ok(())
    }
}

impl<'local> JWasmtimeFuncRef<'local> {
    pub fn from_func<T>(
        env: &mut ::jni::Env<'local>,
        store: &T,
        instance: &JWasmtimeInstance<'local>,
        func: Func,
    ) -> Result<Self, jni::errors::Error>
    where
        T: AsContext<Data = StoreContent>,
    {
        debug!("Converting wasm type 'FuncRef' to java type 'WasmtimeFuncRef'");
        let handle = FuncHandle::new(func);

        let context = store.as_context();
        let data = context.data();
        let w: StoreHandle = data.store_content_ptr.unwrap().into();

        JWasmtimeFuncRef::new(env, handle, w, instance)
    }
}
