use crate::wasminstance::{
    convert_java_array_to_val_vector, convert_val_vector_to_java_array, handle_wasmtime_error,
};
use crate::wasmsharedmemory::SharedMemoryHandle;
use crate::wasmstore::StoreHandle;
use crate::wasmtime_valtype::convert_val_type_enum_list_to_vec;
use crate::wasmtimefunction::JWasmtimeFunction;
use crate::{
    wasmcontext::JWasmContext, wasmengine::EngineHandle, wasmengine::JWasmtimeEngine,
    wasmstore::JWasmtimeStore, wasmstore::StoreContent,
};
use jni::{bind_java_type, sys::jlong};
use log::{debug, error};
use wasmtime::{Func, FuncType, Linker};

#[repr(transparent)]
#[derive(Copy, Clone)]
pub struct LinkerHandle(*mut Linker<StoreContent>);

#[allow(dead_code)]
impl LinkerHandle {
    pub fn new(linker: Linker<StoreContent>) -> Self {
        let boxed = Box::new(linker);
        LinkerHandle(Box::into_raw(boxed))
    }

    pub unsafe fn as_ref(&self) -> &mut Linker<StoreContent> {
        unsafe { &mut *self.0 }
    }

    pub unsafe fn into_box(self) -> Box<Linker<StoreContent>> {
        unsafe { Box::from_raw(self.0 as *mut Linker<StoreContent>) }
    }
}

impl From<LinkerHandle> for jlong {
    fn from(linker: LinkerHandle) -> Self {
        linker.0 as jlong
    }
}

bind_java_type! {
    rust_type = pub JWasmtimeLinker,
    java_type = "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeLinker",

    type_map = {
        unsafe EngineHandle => long,
        unsafe LinkerHandle => long,
        unsafe StoreHandle => long,
        unsafe SharedMemoryHandle => long,
        JWasmtimeEngine => "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeEngine",
        JWasmtimeStore => "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeStore",
        JWasmContext => "io.github.stefanrichterhuber.wasmtimejavang.WasmContext",
        JWasmtimeFunction => "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeFunction",
    },

    fields = {
        linker_ptr: jlong,
        engine: JWasmtimeEngine,
        store: JWasmtimeStore
    },

    constructors {
        fn new(engine: JWasmtimeEngine, store:JWasmtimeStore),
    },

    methods {
        fn link(context: JWasmContext)
    },

    native_methods {
        extern fn create_linker(engine: EngineHandle) -> jlong,
        extern fn close_linker(linker: LinkerHandle),
        extern fn define_function(
            engine: EngineHandle,
            store: StoreHandle,
            linker: LinkerHandle,
            func: JWasmtimeFunction,
            module: JString,
            name: JString,
            parameters: JList,
            return_types: JList),
        extern fn define_memory(store: StoreHandle, linker: LinkerHandle, shared_memory: SharedMemoryHandle, module: JString, name: JString)
    }
}

impl JWasmtimeLinkerNativeInterface for JWasmtimeLinkerAPI {
    type Error = jni::errors::Error;

    fn create_linker<'local>(
        _env: &mut ::jni::Env<'local>,
        _this: JWasmtimeLinker<'local>,
        engine: EngineHandle,
    ) -> ::std::result::Result<::jni::sys::jlong, Self::Error> {
        let linker = Linker::new(unsafe { engine.as_ref() });

        let result = LinkerHandle::new(linker);
        debug!("Created Linker");
        Ok(result.into())
    }

    fn close_linker<'local>(
        _env: &mut ::jni::Env<'local>,
        _this: JWasmtimeLinker<'local>,
        linker: LinkerHandle,
    ) -> ::std::result::Result<(), Self::Error> {
        debug!("Linker closed");
        drop(unsafe { linker.into_box() });
        Ok(())
    }

    fn define_function<'local>(
        env: &mut ::jni::Env<'local>,
        _this: JWasmtimeLinker<'local>,
        engine: EngineHandle,
        store: StoreHandle,
        linker: LinkerHandle,
        func: JWasmtimeFunction,
        module: ::jni::objects::JString<'local>,
        name: ::jni::objects::JString<'local>,
        parameters: ::jni::objects::JList<'local>,
        return_types: ::jni::objects::JList<'local>,
    ) -> ::std::result::Result<(), Self::Error> {
        let params = convert_val_type_enum_list_to_vec(env, parameters)?;
        let results = convert_val_type_enum_list_to_vec(env, return_types)?;

        let func_name = name.to_string();

        debug!(
            "Defining function: {}::{}({:?}) -> {:?}",
            module, name, params, results
        );
        let signature = FuncType::new(unsafe { engine.as_ref() }, params, results.clone());

        // Create a global reference to the function
        let func = env.new_global_ref(func)?;
        let jvm = env.get_java_vm()?;

        let dynamic_func = Func::new(store, signature, move |mut caller, args, returns| {
            debug!("Dynamic call of triggered with {} arguments!", args.len());

            // To prevent aliasing violations (Undefined Behavior) when Java calls back into Rust (re-entrancy),
            // we register the current active `Caller` in thread-local storage for this specific `Store`.
            // Any native method called during this JNI invocation that takes a `StoreHandle` will 
            // now safely use this `Caller` context instead of attempting to create a second 
            // mutable reference to the same `Store`.
            let store_ptr_val = caller
                .data()
                .store_content_ptr
                .expect("Store pointer missing in StoreContent") as usize;
            let _guard = crate::wasmstore::CallerGuard::new(store_ptr_val, &mut caller);

            let result: std::result::Result<Vec<wasmtime::Val>, jni::errors::Error> = jvm
                .attach_current_thread(|env| {
                    // We need to the the instance object from the store map
                    let java_map = &caller.data().context;
                    let instance_obj = match caller.data().instance.as_ref() {
                        Some(instance) => instance,
                        None => {
                            error!("Field instance not found in store");
                            return Err(jni::errors::Error::FieldNotFound {
                                name: "instance".to_owned(),
                                sig: "io/github/stefanrichterhuber/wasmtimejavang/WasmtimeInstance"
                                    .to_owned(),
                            });
                        }
                    };

                    // Convert the args to a JObjectArray
                    let args_array = convert_val_vector_to_java_array(env, &caller, args)?;
                    let result_array = func.call(env, instance_obj, java_map, args_array)?;
                    env.exception_catch()?;
                    let result =
                        convert_java_array_to_val_vector(env, &mut caller, result_array, &results)?;

                    debug!("Dynamic call was successfull: {:?}", result);

                    Ok(result)
                });

            match result {
                Ok(values) => {
                    for (i, v) in values.iter().enumerate() {
                        if i < returns.len() {
                            returns[i] = *v;
                        }
                    }
                    Ok(())
                }
                Err(e) => {
                    debug!("Failed to invoke java function {}: {}", func_name, e);
                    Err(convert_jvm_error_to_wasmtime_error(e))
                }
            }
        });
        // 3. Add to Linker
        let linker = unsafe { linker.as_ref() };
        match linker.define(store, &module.to_string(), &name.to_string(), dynamic_func) {
            Ok(_) => Ok(()),
            Err(e) => handle_wasmtime_error(env, e),
        }
    }

    fn define_memory<'local>(
        env: &mut ::jni::Env<'local>,
        _this: JWasmtimeLinker<'local>,
        store: StoreHandle,
        linker: LinkerHandle,
        shared_memory: SharedMemoryHandle,
        module: ::jni::objects::JString<'local>,
        name: ::jni::objects::JString<'local>,
    ) -> ::std::result::Result<(), Self::Error> {
        let linker = unsafe { linker.as_ref() };
        let shared_memory = unsafe { shared_memory.as_ref() };
        let module = module.to_string();
        let name = name.to_string();

        linker.allow_shadowing(true);
        match linker.define(store, &module, &name, shared_memory.clone()) {
            Ok(_) => Ok(()),
            Err(e) => handle_wasmtime_error(env, e),
        }
    }
}

///
/// Converts jni::errors::Error to wasmtime::error:Error
///
pub fn convert_jvm_error_to_wasmtime_error(
    jvm_error: jni::errors::Error,
) -> wasmtime::error::Error {
    let mut result = wasmtime::error::Error::msg(jvm_error.to_string());

    match jvm_error {
        jni::errors::Error::CaughtJavaException { stack, .. } => {
            result = result.context(stack);
        }
        _ => {}
    }

    result
}
