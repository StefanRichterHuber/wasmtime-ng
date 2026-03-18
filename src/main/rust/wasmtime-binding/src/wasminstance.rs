use std::cell::RefCell;

use crate::java_numbers::JDouble;
use crate::java_numbers::JFloat;
use crate::java_numbers::JInteger;
use crate::java_numbers::JLong;
use crate::java_numbers::JNumber;
use crate::wasmengine::EngineHandle;
use crate::wasmexception::handle_wasmtime_error;
use crate::wasmlinker::JWasmtimeLinker;
use crate::wasmlinker::LinkerHandle;
use crate::wasmmemory::JWasmtimeLocalMemory;
use crate::wasmmodule::JWasmtimeModule;
use crate::wasmmodule::ModuleHandle;
use crate::wasmstore::JWasmtimeStore;
use crate::wasmstore::StoreContent;
use crate::wasmstore::StoreHandle;
use crate::wasmtime_v128::JV128;
use crate::wasmtimefuncref::JWasmtimeFuncRef;
use crate::wasmtimefunction::JWasmtimeFunction;
use jni::objects::JClass;
use jni::refs::Global;
use jni::refs::LoaderContext;
use jni::refs::Reference;
use jni::{
    bind_java_type, jni_str,
    objects::{JObject, JObjectArray},
    strings::JNIString,
    sys::{jlong, jsize},
};
use log::{debug, error, warn};
use wasmtime::AsContext;
use wasmtime::AsContextMut;
use wasmtime::ValType;
use wasmtime::{ExternRef, Instance, RefType, Val};

#[repr(transparent)]
#[derive(Copy, Clone)]
pub struct InstanceHandle(*const Instance);

#[allow(dead_code)]
impl InstanceHandle {
    pub fn new(instance: Instance) -> Self {
        let boxed = Box::new(instance);
        InstanceHandle(Box::into_raw(boxed))
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

impl From<InstanceHandle> for jlong {
    fn from(handle: InstanceHandle) -> Self {
        handle.0 as jlong
    }
}

bind_java_type! {
    rust_type = pub JWasmtimeInstance,
    java_type = "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeInstance",

    type_map = {
        unsafe EngineHandle => long,
        unsafe StoreHandle => long,
        unsafe ModuleHandle => long,
        unsafe LinkerHandle => long,
        unsafe InstanceHandle => long,
        JWasmtimeStore => "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeStore",
        JWasmtimeLinker => "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeLinker",
        JWasmtimeModule =>  "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeModule",
        JWasmtimeFunction =>  "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeFunction",
        JWasmtimeLocalMemory => "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeLocalMemory",
        JWasmtimeFuncRef => "io.github.stefanrichterhuber.wasmtimejavang.internal.WasmtimeFuncRef",
    },

    constructors {
        fn new(store: JWasmtimeStore, module: JWasmtimeModule, linker: JWasmtimeLinker),
    },

    fields {
        instantace_ptr: jlong,
        store: JWasmtimeStore,
        module: JWasmtimeModule,
        linker: JWasmtimeLinker,
    },

    methods {
       fn invoke(name: JString, args: JObject[]) -> JObject[],
       fn get_function(name: JString) -> JWasmtimeFunction,
       fn get_memory(name: JString) -> JWasmtimeLocalMemory,
    },

    native_methods {
        extern fn create_instance(module: ModuleHandle, store: StoreHandle, linker: LinkerHandle ) -> jlong,
        extern fn close_instance(instance: InstanceHandle),
        extern fn run_wasm_func(store: StoreHandle, instance: InstanceHandle, name: JString, parameters: JObject[]) -> JObject[],
        extern fn get_function_reference(store: StoreHandle,instance: InstanceHandle, name: JString) -> JWasmtimeFuncRef,
    }
}

thread_local! {
    static ACTIVE_INSTANCE: RefCell<Vec<Global<JWasmtimeInstance<'static>>>> = const { RefCell::new(Vec::new()) };
}

struct InstanceGuard(bool);
impl Drop for InstanceGuard {
    fn drop(&mut self) {
        if self.0 {
            ACTIVE_INSTANCE.with(|stack| stack.borrow_mut().pop());
            debug!("InstanceGuard dropped, with pop");
        } else {
            debug!("InstanceGuard dropped, without pop, since same instance was already on stack");
        }
    }
}

///
/// Ensures that there is always a JWasmtimeInstance object available.
/// Each time a function is called with a JWasmtimeInstance it is pushed on the thread local stack.
/// If the same instance is called multiple times, it is not pushed again.
/// If no instance is provided, the last instance on the stack is used.
///
pub fn with_instance<'local, F, R>(
    env: &mut ::jni::Env<'local>,
    instance: Option<Global<JWasmtimeInstance<'static>>>,
    f: F,
) -> Result<R, jni::errors::Error>
where
    F: FnOnce(
        &mut ::jni::Env<'local>,
        &Global<JWasmtimeInstance<'static>>,
    ) -> Result<R, jni::errors::Error>,
{
    if let Some(current_instance) = instance {
        let _guard: Result<InstanceGuard, jni::errors::Error> = ACTIVE_INSTANCE.with(|stack| {
            let mut s = stack.borrow_mut();

            if let Some(current) = s.last() {
                // If the last instance on the stack is the same as the given one, don't push another copy and return a no-op guard
                if env.is_same_object(&current_instance, current)? {
                    debug!("With instance: {:?} (same)", current_instance);
                    Ok(InstanceGuard(false))
                } else {
                    let global_copy = env.new_global_ref(&current_instance)?;
                    debug!("With instance: {:?} (new)", global_copy);
                    s.push(global_copy);
                    Ok(InstanceGuard(true))
                }
            } else {
                let global_copy = env.new_global_ref(&current_instance)?;
                debug!("With instance: {:?} (new, none before)", global_copy);
                s.push(global_copy);
                Ok(InstanceGuard(true))
            }
        });
        let _guard = _guard?;
        f(env, &current_instance)
    } else {
        ACTIVE_INSTANCE
            .with(|stack| match stack.borrow().last() {
                Some(reference) => {
                    debug!("Found instance on instance stack: {:?}", reference);
                    env.new_global_ref(reference)
                }
                None => {
                    error!("No instance on instance stack");
                    Err(jni::errors::Error::NullPtr("No instance on stack"))
                }
            })
            .and_then(|global| f(env, &global))
    }
}

impl JWasmtimeInstanceNativeInterface for JWasmtimeInstanceAPI {
    type Error = jni::errors::Error;

    fn close_instance<'local>(
        _env: &mut ::jni::Env<'local>,
        _this: JWasmtimeInstance<'local>,
        instance: InstanceHandle,
    ) -> ::std::result::Result<(), Self::Error> {
        debug!("Closing Instance");
        drop(unsafe { instance.into_box() });
        debug!("Instance closed successfully");
        Ok(())
    }

    fn run_wasm_func<'local>(
        env: &mut ::jni::Env<'local>,
        this: JWasmtimeInstance<'local>,
        store: StoreHandle,
        instance: InstanceHandle,
        name: ::jni::objects::JString<'local>,
        parameters: ::jni::objects::JObjectArray<'local>,
    ) -> ::std::result::Result<::jni::objects::JObjectArray<'local>, Self::Error> {
        let instance = unsafe { instance.as_ref() };
        let instance_obj = env.new_global_ref(this)?;
        let name = name.to_string();

        
        with_instance(env, Some(instance_obj), |env, _| {
            match instance.get_func(store, &name) {
                Some(func) => {
                    let param_types: Vec<wasmtime::ValType> = func.ty(store).params().collect();
                    let result_types: Vec<wasmtime::ValType> = func.ty(store).results().collect();
                    let args =
                        convert_java_array_to_val_vector(env, store, parameters, &param_types)?;

                    let result_len = result_types.len();
                    let mut results = vec![Val::I64(0); result_len];

                    match func.call(store, &args, &mut results) {
                        Ok(()) => {
                            debug!("Successfully called function {}", name);
                            convert_val_vector_to_java_array(env, &store, &results)
                        }
                        Err(e) => {
                            debug!("Failed to call function {}: {}", name, e);
                            handle_wasmtime_error(env, e)?;
                            empty_array(env, result_len.try_into().unwrap())
                        }
                    }
                }
                None => {
                    let msg = format!("No function found with name {}", name);
                    let msg = JNIString::from(msg);
                    env.throw_new(jni_str!("java/lang/RuntimeException"), msg)?;
                    empty_array(env, 0)
                }
            }
        })
    }

    fn create_instance<'local>(
        env: &mut ::jni::Env<'local>,
        _this: JWasmtimeInstance<'local>,
        module: ModuleHandle,
        store: StoreHandle,
        mut linker: LinkerHandle,
    ) -> ::std::result::Result<::jni::sys::jlong, Self::Error> {
        let linker = unsafe { linker.as_ref() };
        debug!("Start createing new instance");
        let result = match linker.instantiate(store, unsafe { module.as_ref() }) {
            Ok(i) => {
                debug!("Successfully created new instance: {:?}", i);
                InstanceHandle::new(i).into()
            }
            Err(e) => {
                error!("Failed to create new instance: {}", e);
                handle_wasmtime_error(env, e)?;
                0
            }
        };

        // Store a reference to the java instance in the store
        debug!("Created Instance");
        Ok(result)
    }

    fn get_function_reference<'local>(
        env: &mut ::jni::Env<'local>,
        _this: JWasmtimeInstance<'local>,
        store: StoreHandle,
        instance: InstanceHandle,
        name: ::jni::objects::JString<'local>,
    ) -> ::std::result::Result<JWasmtimeFuncRef<'local>, Self::Error> {
        let instance = unsafe { instance.as_ref() };
        let name = name.to_string();
        let func = instance.get_func(store, &name);

        if let Some(f) = func {
            let func_object = JWasmtimeFuncRef::from_func(env, &store, f)?;
            Ok(func_object)
        } else {
            Ok(JWasmtimeFuncRef::null())
        }
    }
}

///
/// Creates an empty java Object[] array filled with null
///
pub fn empty_array<'local>(
    env: &mut ::jni::Env<'local>,
    len: jsize,
) -> Result<JObjectArray<'local>, jni::errors::Error> {
    let array = env.new_object_array(len, jni_str!("java.lang.Number"), JObject::null())?;
    Ok(array)
}

///
/// Container used to store any java object in an extern ref
///
pub struct ExternRefContainer {
    pub value: Global<JObject<'static>>,
}

impl ExternRefContainer {
    pub fn new(value: Global<JObject<'static>>) -> Self {
        ExternRefContainer { value }
    }
}

///
/// Converts a wasmtime Val to a java object
///
pub fn convert_val_to_java_object<'local, T>(
    env: &mut ::jni::Env<'local>,
    store: &T,
    value: &Val,
) -> Result<JObject<'local>, jni::errors::Error>
where
    T: AsContext<Data = StoreContent>,
{
    let obj: Option<JObject> = match value {
        Val::I32(v) => {
            let obj = JInteger::value_of(env, *v)?;
            Some(obj.into())
        }
        Val::I64(v) => {
            let obj = JLong::value_of(env, *v)?;
            Some(obj.into())
        }
        Val::F32(v) => {
            let obj = JFloat::value_of(env, f32::from_bits(*v))?;
            Some(obj.into())
        }
        Val::F64(v) => {
            let obj = JDouble::value_of(env, f64::from_bits(*v))?;
            Some(obj.into())
        }
        Val::V128(v128) => {
            debug!("Converting V128 value to java V128");
            let v128_object = JV128::from_v128(env, v128)?;
            Some(v128_object.into())
        }
        Val::FuncRef(func) => {
            if let Some(f) = func {
                let func_object = JWasmtimeFuncRef::from_func(env, store, *f)?;
                Some(func_object.into())
            } else {
                None
            }
        }
        Val::ExternRef(rooted) => {
            if let Some(reference) = rooted {
                let global = reference
                    .data(store)
                    .and_then(|global| {
                        global.ok_or(wasmtime::Error::msg("failed to unpack externref"))
                    })
                    .and_then(|global| {
                        global
                            .downcast_ref::<ExternRefContainer>()
                            .ok_or(wasmtime::Error::msg("failed to downcast externref"))
                    });

                
                match global {
                    Ok(g) => {
                        debug!("Successfully unpacked ExternRef value");
                        Some(unsafe { JObject::from_raw(env, g.value.as_obj().as_raw()) })
                    }
                    Err(e) => {
                        error!("Failed to unpack ExternRef value: {}", e);
                        None
                    }
                }
            } else {
                None
            }
        }
        Val::AnyRef(_rooted) => {
            warn!("Unsupported wasm type to java conversion: AnyRef");
            None
        }
        Val::ExnRef(_rooted) => {
            warn!("Unsupported wasm type to java conversion: ExnRef");
            None
        }
        Val::ContRef(_cont_ref) => {
            warn!("Unsupported wasm type to java conversion: ContRef");
            None
        }
    };

    match obj {
        Some(v) => Ok(v),
        None => Ok(JObject::null()),
    }
}

///
/// Converts a vec<Val> to a java Object[]
///
pub fn convert_val_vector_to_java_array<'local, T>(
    env: &mut ::jni::Env<'local>,
    store: &T,
    values: &[Val],
) -> Result<JObjectArray<'local>, jni::errors::Error>
where
    T: AsContext<Data = StoreContent>,
{
    let len = values.len();

    let array = env.new_object_array(
        len.try_into().unwrap(),
        jni_str!("java.lang.Object"),
        JObject::null(),
    )?;

    for (index, v) in values.iter().enumerate() {
        let obj_ref = convert_val_to_java_object(env, store, v)?;
        array.set_element(env, index, obj_ref)?;
    }

    Ok(array)
}

///
///  Converts a single java object into the corresponding wasm Val
///
pub fn convert_java_object_to_val<'local, T>(
    env: &mut ::jni::Env<'local>,
    store: T,
    item: JObject<'local>,
    val_type: &ValType,
) -> Result<Val, jni::errors::Error>
where
    T: AsContextMut<Data = StoreContent>,
{
    let val = match val_type {
        wasmtime::ValType::I32 => {
            if item.is_null() {
                Val::I32(0)
            } else {
                debug!("Convert Number to Val::I32");
                let o = env.cast_local::<JNumber>(item)?;
                let v = o.int_value(env)?;
                Val::I32(v)
            }
        }
        wasmtime::ValType::I64 => {
            if item.is_null() {
                Val::I64(0)
            } else {
                debug!("Convert Number to Val::I64");
                let o = env.cast_local::<JNumber>(item)?;
                let v = o.long_value(env)?;

                Val::I64(v)
            }
        }
        wasmtime::ValType::F32 => {
            if item.is_null() {
                Val::F32(0)
            } else {
                debug!("Convert Number to Val::F32");
                let o = env.cast_local::<JNumber>(item)?;
                let v = o.float_value(env)?;
                Val::F32(v.to_bits())
            }
        }
        wasmtime::ValType::F64 => {
            if item.is_null() {
                Val::F64(0)
            } else {
                debug!("Convert Number to Val::F64");
                let o = env.cast_local::<JNumber>(item)?;
                let v = o.double_value(env)?;
                Val::F64(v.to_bits())
            }
        }
        wasmtime::ValType::V128 => {
            if item.is_null() {
                Val::V128(u128::default().into())
            } else {
                let v128_class = JV128::lookup_class(env, &LoaderContext::None)?;
                let v128_class: &JClass = v128_class.as_ref();

                debug!("Convert Number to Val::V128");
                let o = env.cast_local::<JNumber>(item)?;

                if env.is_instance_of(&o, v128_class)? {
                    let item = env.cast_local::<JV128>(o)?;
                    Val::V128(item.into_v128(env)?)
                } else {
                    let value = o.long_value(env)?;
                    let value = u64::from_ne_bytes(value.to_ne_bytes());

                    Val::V128(u128::from(value).into())
                }
            }
        }
        wasmtime::ValType::Ref(ref_type) => {
            if ref_type.matches(&RefType::EXTERNREF) || ref_type.matches(&RefType::NULLEXTERNREF) {
                debug!("Creating EXTERNREF from any java object");
                let global_item = env.new_global_ref(item)?;
                let container = ExternRefContainer::new(global_item);
                let r = ExternRef::new(store, container).map(|r| Val::ExternRef(Some(r)));

                match r {
                    Ok(r) => r,
                    Err(_e) => {
                        error!("Failed to pack java object into wasm ExternRef");
                        Val::null_extern_ref()
                    }
                }
            } else {
                warn!(
                    "Extern ref type {} conversion from java to wasm not supported",
                    ref_type
                );
                Val::null_any_ref()
            }
        }
    };

    Ok(val)
}

///
/// Converts  a java Object[] to a vec<Val>  
///
pub fn convert_java_array_to_val_vector<'local, T>(
    env: &mut ::jni::Env<'local>,
    mut store: T,
    values: JObjectArray,
    param_types: &[wasmtime::ValType],
) -> Result<Vec<Val>, jni::errors::Error>
where
    T: AsContextMut<Data = StoreContent>,
{
    // It is ensured that we always deliver the correct number of wasm Vals,
    // no matter if the given java array is null or too short.
    // Missing values are replaced with java null
    let mut result = Vec::with_capacity(param_types.len());
    let values_len = if values.is_null() {
        if !param_types.is_empty() {
            warn!(
                "Wasm function expects {} args, but given args are null. All args are considered as java null",
                param_types.len()
            );
        }
        0
    } else {
        let values_len = values.len(env)?;
        if values_len < param_types.len() {
            warn!(
                "Wasm function expects {} args, but only {} args are given. Missing args are considered as java null",
                param_types.len(),
                values_len
            );
        }
        values_len
    };

    for (index, val_type) in param_types.iter().enumerate() {
        let value = if index < values_len {
            values.get_element(env, index)?
        } else {
            debug!("Java value array to short / or null, generate wasm value from java null");
            JObject::null()
        };
        let val = convert_java_object_to_val(env, &mut store, value, val_type)?;
        result.push(val);
    }
    Ok(result)
}
