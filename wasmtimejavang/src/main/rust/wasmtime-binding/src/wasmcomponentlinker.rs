use crate::wasmcomponentinstance::with_component_instance;
use crate::wasmcomponentvalue::{java_object_to_val, resource_type_for, val_to_java_object};
use crate::wasmexception::{convert_jvm_error_to_wasmtime_error, handle_wasmtime_error};
use crate::wasmstore::{CallerGuard, StoreContent, StoreHandle};
use jni::objects::{JClass, JList};
use jni::{bind_java_type, jni_str, sys::jlong};
use log::debug;
use wasmtime::StoreContextMut;
use wasmtime::component::types::ComponentFunc;
use wasmtime::component::{Linker, LinkerInstance, Val};

#[repr(transparent)]
#[derive(Copy, Clone)]
pub struct ComponentLinkerHandle(*mut Linker<StoreContent>);

#[allow(dead_code)]
impl ComponentLinkerHandle {
    pub fn new(linker: Linker<StoreContent>) -> Self {
        let boxed = Box::new(linker);
        ComponentLinkerHandle(Box::into_raw(boxed))
    }

    /// Returns a mutable reference to the underlying `Linker`.
    ///
    /// # Safety
    /// The caller must ensure that the underlying raw pointer is valid and that the `Linker` it points to has not been dropped.
    /// As this returns a mutable reference, the caller must also ensure that no other references (mutable or immutable) to the same `Linker` exist simultaneously to avoid aliasing violations.
    pub unsafe fn as_ref(&mut self) -> &mut Linker<StoreContent> {
        unsafe { &mut *self.0 }
    }

    /// Converts the raw pointer back into a `Box<Linker<StoreContent>>`.
    ///
    /// # Safety
    /// The caller must ensure that the underlying raw pointer is valid and has not already been freed.
    /// Calling this method transfers ownership to the returned `Box`, so the raw pointer must not be used afterwards to avoid double-free or use-after-free errors.
    pub unsafe fn into_box(self) -> Box<Linker<StoreContent>> {
        unsafe { Box::from_raw(self.0) }
    }
}

impl From<ComponentLinkerHandle> for jlong {
    fn from(linker: ComponentLinkerHandle) -> Self {
        linker.0 as jlong
    }
}

bind_java_type! {
    rust_type = pub JComponentFunction,
    java_type = "io.github.stefanrichterhuber.wasmtimejavang.ComponentFunction",

    type_map = {
        crate::wasmcomponentinstance::JWasmtimeComponentInstance => "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance",
    },

    methods = {
        fn call(instance: crate::wasmcomponentinstance::JWasmtimeComponentInstance, args: JObject[]) -> JObject[],
    },
}

bind_java_type! {
    rust_type = pub JResourceDestructor,
    java_type = "io.github.stefanrichterhuber.wasmtimejavang.ResourceDestructor",

    methods = {
        fn drop_resource {
            name = "drop",
            sig = (rep: jint) -> void,
        },
    },
}

bind_java_type! {
    rust_type = pub JWasmtimeComponentLinker,
    java_type = "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentLinker",

    type_map = {
        unsafe crate::wasmengine::EngineHandle => long,
        unsafe ComponentLinkerHandle => long,
        unsafe StoreHandle => long,
        crate::wasmengine::JWasmtimeEngine => "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeEngine",
        crate::wasmstore::JWasmtimeStore => "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeStore",
        JComponentFunction => "io.github.stefanrichterhuber.wasmtimejavang.ComponentFunction",
        JResourceDestructor => "io.github.stefanrichterhuber.wasmtimejavang.ResourceDestructor",
    },

    fields = {
        linker_ptr: jlong,
        engine: crate::wasmengine::JWasmtimeEngine,
        store: crate::wasmstore::JWasmtimeStore,
    },

    constructors {
        fn new(engine: crate::wasmengine::JWasmtimeEngine, store: crate::wasmstore::JWasmtimeStore),
    },

    native_methods {
        extern fn create_linker(engine: crate::wasmengine::EngineHandle) -> jlong,
        extern static fn close_linker(linker: ComponentLinkerHandle),
        extern fn define_component_interface(
            store: StoreHandle,
            linker: ComponentLinkerHandle,
            interface_name: JString,
            resource_names: JList,
            destructors: JList,
            func_names: JList,
            functions: JList),
    }
}

impl JWasmtimeComponentLinkerNativeInterface for JWasmtimeComponentLinkerAPI {
    type Error = jni::errors::Error;

    fn create_linker<'local>(
        _env: &mut ::jni::Env<'local>,
        _this: JWasmtimeComponentLinker<'local>,
        engine: crate::wasmengine::EngineHandle,
    ) -> ::std::result::Result<::jni::sys::jlong, Self::Error> {
        let linker = Linker::new(unsafe { engine.as_ref() });
        let result = ComponentLinkerHandle::new(linker);
        debug!("Created component Linker");
        Ok(result.into())
    }

    fn close_linker<'local>(
        _env: &mut ::jni::Env<'local>,
        _class: JClass<'local>,
        linker: ComponentLinkerHandle,
    ) -> ::std::result::Result<(), Self::Error> {
        debug!("Component linker closed");
        drop(unsafe { linker.into_box() });
        Ok(())
    }

    /// Registers every resource and function belonging to a single component
    /// interface (or the root instance, if `interface_name` is empty).
    ///
    /// This is batched per-interface (rather than one native call per
    /// function/resource) because `component::Linker::instance(name)` errors
    /// if called more than once for the same name -- every import belonging
    /// to one interface must be registered while a single `LinkerInstance`
    /// borrow is alive.
    fn define_component_interface<'local>(
        env: &mut ::jni::Env<'local>,
        _this: JWasmtimeComponentLinker<'local>,
        _store: StoreHandle,
        mut linker: ComponentLinkerHandle,
        interface_name: ::jni::objects::JString<'local>,
        resource_names: JList<'local>,
        destructors: JList<'local>,
        func_names: JList<'local>,
        functions: JList<'local>,
    ) -> ::std::result::Result<(), Self::Error> {
        let interface_name = interface_name.to_string();
        debug!("Defining component interface: {}", interface_name);

        let linker = unsafe { linker.as_ref() };
        let mut linker_instance: LinkerInstance<'_, StoreContent> = if interface_name.is_empty() {
            linker.root()
        } else {
            match linker.instance(&interface_name) {
                Ok(li) => li,
                Err(e) => return handle_wasmtime_error(env, e),
            }
        };

        let resource_name_iter = resource_names.iter(env)?;
        let destructor_iter = destructors.iter(env)?;
        loop {
            let name = resource_name_iter.next(env)?;
            let dtor = destructor_iter.next(env)?;
            let (name, dtor) = match (name, dtor) {
                (Some(n), Some(d)) => (n, d),
                _ => break,
            };
            let resource_name = env.cast_local::<::jni::objects::JString>(name)?.to_string();
            let destructor = env.cast_local::<JResourceDestructor>(dtor)?;

            debug!(
                "Defining component resource: {}#{}",
                interface_name, resource_name
            );

            let destructor = env.new_global_ref(destructor)?;
            let jvm = env.get_java_vm()?;
            let resource_type = resource_type_for(&resource_name);

            let define_result = linker_instance.resource(
                &resource_name,
                resource_type,
                move |mut store_ctx: StoreContextMut<'_, StoreContent>, rep: u32| {
                    let store_ptr_val = store_ctx
                        .data()
                        .store_content_ptr
                        .expect("Store pointer missing in StoreContent")
                        as usize;
                    let _guard = CallerGuard::new_from_store_context(store_ptr_val, &mut store_ctx);

                    let call_result: std::result::Result<(), jni::errors::Error> =
                        jvm.attach_current_thread(|env| destructor.drop_resource(env, rep as i32));

                    if let Err(e) = call_result {
                        debug!("Failed to invoke java resource destructor: {}", e);
                        return Err(convert_jvm_error_to_wasmtime_error(e));
                    }
                    Ok(())
                },
            );
            if let Err(e) = define_result {
                return handle_wasmtime_error(env, e);
            }
        }

        let func_name_iter = func_names.iter(env)?;
        let function_iter = functions.iter(env)?;
        loop {
            let name = func_name_iter.next(env)?;
            let function = function_iter.next(env)?;
            let (name, function) = match (name, function) {
                (Some(n), Some(f)) => (n, f),
                _ => break,
            };
            let func_name = env.cast_local::<::jni::objects::JString>(name)?.to_string();
            let function = env.cast_local::<JComponentFunction>(function)?;

            debug!("Defining component function: {}#{}", interface_name, func_name);

            let func = env.new_global_ref(function)?;
            let jvm = env.get_java_vm()?;
            let func_name_for_closure = func_name.clone();

            let define_result = linker_instance.func_new(
                &func_name,
                move |mut store_ctx: StoreContextMut<'_, StoreContent>,
                      ty: ComponentFunc,
                      params: &[Val],
                      results: &mut [Val]| {
                    let store_ptr_val = store_ctx
                        .data()
                        .store_content_ptr
                        .expect("Store pointer missing in StoreContent")
                        as usize;
                    let _guard = CallerGuard::new_from_store_context(store_ptr_val, &mut store_ctx);

                    let result_types: Vec<_> = ty.results().collect();

                    let call_result: std::result::Result<Vec<Val>, jni::errors::Error> =
                        jvm.attach_current_thread(|env| {
                            with_component_instance(env, None, |env, instance_obj| {
                                let args_array = env.new_object_array(
                                    params.len() as i32,
                                    jni_str!("java.lang.Object"),
                                    jni::objects::JObject::null(),
                                )?;
                                for (index, v) in params.iter().enumerate() {
                                    let obj = val_to_java_object(env, &mut store_ctx, v)?;
                                    args_array.set_element(env, index, obj)?;
                                }

                                let result_array = func.call(env, instance_obj, args_array)?;

                                let mut values = Vec::with_capacity(result_types.len());
                                let values_len = if result_array.is_null() {
                                    0
                                } else {
                                    result_array.len(env)?
                                };
                                for (index, ty) in result_types.iter().enumerate() {
                                    let value = if index < values_len {
                                        result_array.get_element(env, index)?
                                    } else {
                                        jni::objects::JObject::null()
                                    };
                                    values.push(java_object_to_val(env, &mut store_ctx, value, ty)?);
                                }
                                Ok(values)
                            })
                        });

                    match call_result {
                        Ok(values) => {
                            for (i, v) in values.into_iter().enumerate() {
                                if i < results.len() {
                                    results[i] = v;
                                }
                            }
                            Ok(())
                        }
                        Err(e) => {
                            debug!(
                                "Failed to invoke java component function {}: {}",
                                func_name_for_closure, e
                            );
                            Err(convert_jvm_error_to_wasmtime_error(e))
                        }
                    }
                },
            );
            if let Err(e) = define_result {
                return handle_wasmtime_error(env, e);
            }
        }

        Ok(())
    }
}
