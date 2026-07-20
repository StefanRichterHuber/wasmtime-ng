use std::cell::RefCell;

use crate::wasmcomponent::ComponentHandle;
use crate::wasmcomponent::JWasmtimeComponent;
use crate::wasmcomponentlinker::ComponentLinkerHandle;
use crate::wasmcomponentlinker::JWasmtimeComponentLinker;
use crate::wasmcomponentvalue::{java_object_to_val, val_to_java_object};
use crate::wasmexception::handle_wasmtime_error;
use crate::wasmstore::JWasmtimeStore;
use crate::wasmstore::StoreHandle;
use jni::objects::JClass;
use jni::refs::Global;
use jni::strings::JNIString;
use jni::{
    bind_java_type,
    objects::JObject,
    sys::jlong,
};
use log::{debug, error};
use wasmtime::component::{Instance, Val};

#[repr(transparent)]
#[derive(Copy, Clone)]
pub struct ComponentInstanceHandle(*const Instance);

#[allow(dead_code)]
impl ComponentInstanceHandle {
    pub fn new(instance: Instance) -> Self {
        let boxed = Box::new(instance);
        ComponentInstanceHandle(Box::into_raw(boxed))
    }

    /// Returns a reference to the underlying `Instance`.
    ///
    /// # Safety
    /// The caller must ensure that the underlying raw pointer is valid and that the `Instance` it points to has not been dropped.
    pub unsafe fn as_ref(&self) -> &Instance {
        unsafe { &*self.0 }
    }

    /// Converts the raw pointer back into a `Box<Instance>`.
    ///
    /// # Safety
    /// The caller must ensure that the underlying raw pointer is valid and has not already been freed.
    /// Calling this method transfers ownership to the returned `Box`, so the raw pointer must not be used afterwards to avoid double-free or use-after-free errors.
    pub unsafe fn into_box(self) -> Box<Instance> {
        unsafe { Box::from_raw(self.0 as *mut Instance) }
    }
}

impl From<ComponentInstanceHandle> for jlong {
    fn from(handle: ComponentInstanceHandle) -> Self {
        handle.0 as jlong
    }
}

bind_java_type! {
    rust_type = pub JWasmtimeComponentInstance,
    java_type = "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance",

    type_map = {
        unsafe StoreHandle => long,
        unsafe ComponentHandle => long,
        unsafe ComponentLinkerHandle => long,
        unsafe ComponentInstanceHandle => long,
        JWasmtimeStore => "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeStore",
        JWasmtimeComponentLinker => "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentLinker",
        JWasmtimeComponent =>  "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponent",
    },

    constructors {
        fn new(store: JWasmtimeStore, component: JWasmtimeComponent, linker: JWasmtimeComponentLinker),
    },

    fields {
        instance_ptr: jlong,
        store: JWasmtimeStore,
        component: JWasmtimeComponent,
        linker: JWasmtimeComponentLinker,
    },

    native_methods {
        extern fn create_instance(component: ComponentHandle, store: StoreHandle, linker: ComponentLinkerHandle) -> jlong,
        extern static fn close_instance(instance: ComponentInstanceHandle),
        extern fn call_component_func(store: StoreHandle, instance: ComponentInstanceHandle, interface_name: JString, func_name: JString, parameters: JObject[]) -> JObject[],
    }
}

thread_local! {
    static ACTIVE_COMPONENT_INSTANCE: RefCell<Vec<Global<JWasmtimeComponentInstance<'static>>>> = const { RefCell::new(Vec::new()) };
}

struct ComponentInstanceGuard(bool);
impl Drop for ComponentInstanceGuard {
    fn drop(&mut self) {
        if self.0 {
            ACTIVE_COMPONENT_INSTANCE.with(|stack| stack.borrow_mut().pop());
        }
    }
}

///
/// Ensures that there is always a JWasmtimeComponentInstance object available.
/// Mirrors `wasminstance::with_instance` for the component model.
///
pub fn with_component_instance<'local, F, R>(
    env: &mut ::jni::Env<'local>,
    instance: Option<Global<JWasmtimeComponentInstance<'static>>>,
    f: F,
) -> Result<R, jni::errors::Error>
where
    F: FnOnce(
        &mut ::jni::Env<'local>,
        &Global<JWasmtimeComponentInstance<'static>>,
    ) -> Result<R, jni::errors::Error>,
{
    if let Some(current_instance) = instance {
        let _guard: Result<ComponentInstanceGuard, jni::errors::Error> =
            ACTIVE_COMPONENT_INSTANCE.with(|stack| {
                let mut s = stack.borrow_mut();
                if let Some(current) = s.last() {
                    if env.is_same_object(&current_instance, current)? {
                        Ok(ComponentInstanceGuard(false))
                    } else {
                        let global_copy = env.new_global_ref(&current_instance)?;
                        s.push(global_copy);
                        Ok(ComponentInstanceGuard(true))
                    }
                } else {
                    let global_copy = env.new_global_ref(&current_instance)?;
                    s.push(global_copy);
                    Ok(ComponentInstanceGuard(true))
                }
            });
        let _guard = _guard?;
        f(env, &current_instance)
    } else {
        ACTIVE_COMPONENT_INSTANCE
            .with(|stack| match stack.borrow().last() {
                Some(reference) => env.new_global_ref(reference),
                None => {
                    error!("No component instance on instance stack");
                    Err(jni::errors::Error::NullPtr("No component instance on stack"))
                }
            })
            .and_then(|global| f(env, &global))
    }
}

impl JWasmtimeComponentInstanceNativeInterface for JWasmtimeComponentInstanceAPI {
    type Error = jni::errors::Error;

    fn close_instance<'local>(
        _env: &mut ::jni::Env<'local>,
        _class: JClass<'local>,
        instance: ComponentInstanceHandle,
    ) -> ::std::result::Result<(), Self::Error> {
        debug!("Closing component instance");
        drop(unsafe { instance.into_box() });
        Ok(())
    }

    fn create_instance<'local>(
        env: &mut ::jni::Env<'local>,
        _this: JWasmtimeComponentInstance<'local>,
        component: ComponentHandle,
        store: StoreHandle,
        mut linker: ComponentLinkerHandle,
    ) -> ::std::result::Result<::jni::sys::jlong, Self::Error> {
        let linker = unsafe { linker.as_ref() };
        let result = match linker.instantiate(store, unsafe { component.as_ref() }) {
            Ok(i) => ComponentInstanceHandle::new(i).into(),
            Err(e) => {
                error!("Failed to instantiate component: {}", e);
                handle_wasmtime_error(env, e)?;
                0
            }
        };
        debug!("Created component instance");
        Ok(result)
    }

    fn call_component_func<'local>(
        env: &mut ::jni::Env<'local>,
        this: JWasmtimeComponentInstance<'local>,
        mut store: StoreHandle,
        instance: ComponentInstanceHandle,
        interface_name: ::jni::objects::JString<'local>,
        func_name: ::jni::objects::JString<'local>,
        parameters: ::jni::objects::JObjectArray<'local>,
    ) -> ::std::result::Result<::jni::objects::JObjectArray<'local>, Self::Error> {
        let instance = unsafe { instance.as_ref() };
        let instance_obj = env.new_global_ref(this)?;
        let interface_name = interface_name.to_string();
        let func_name = func_name.to_string();

        with_component_instance(env, Some(instance_obj), |env, _local_instance| {
            let func = if interface_name.is_empty() {
                instance.get_func(&mut store, func_name.as_str())
            } else {
                let iface_index = instance.get_export_index(&mut store, None, &interface_name);
                match iface_index {
                    Some(iface_index) => instance
                        .get_export_index(&mut store, Some(&iface_index), &func_name)
                        .and_then(|func_index| instance.get_func(&mut store, &func_index)),
                    None => None,
                }
            };

            match func {
                Some(func) => {
                    let func_ty = func.ty(&store);
                    let param_types: Vec<_> = func_ty.params().map(|(_, ty)| ty).collect();
                    let result_types: Vec<_> = func_ty.results().collect();

                    let mut args = Vec::with_capacity(param_types.len());
                    let values_len = if parameters.is_null() {
                        0
                    } else {
                        parameters.len(env)?
                    };
                    for (index, ty) in param_types.iter().enumerate() {
                        let value = if index < values_len {
                            parameters.get_element(env, index)?
                        } else {
                            JObject::null()
                        };
                        args.push(java_object_to_val(env, &mut store, value, ty)?);
                    }

                    let mut results = vec![Val::Bool(false); result_types.len()];

                    match func.call(&mut store, &args, &mut results) {
                        Ok(()) => {
                            let array = env.new_object_array(
                                results.len() as i32,
                                jni::jni_str!("java.lang.Object"),
                                JObject::null(),
                            )?;
                            for (index, v) in results.iter().enumerate() {
                                let obj = val_to_java_object(env, &mut store, v)?;
                                array.set_element(env, index, obj)?;
                            }
                            Ok(array)
                        }
                        Err(e) => {
                            handle_wasmtime_error(env, e)?;
                            env.new_object_array(0, jni::jni_str!("java.lang.Object"), JObject::null())
                        }
                    }
                }
                None => {
                    let msg = format!(
                        "No exported component function found for {}#{}",
                        interface_name, func_name
                    );
                    let msg = JNIString::from(msg);
                    env.throw_new(jni::jni_str!("java/lang/RuntimeException"), msg)?;
                    env.new_object_array(0, jni::jni_str!("java.lang.Object"), JObject::null())
                }
            }
        })
    }
}
