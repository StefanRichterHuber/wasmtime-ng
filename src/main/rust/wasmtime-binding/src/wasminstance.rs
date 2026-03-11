use crate::java_numbers::JDouble;
use crate::java_numbers::JFloat;
use crate::java_numbers::JInteger;
use crate::java_numbers::JLong;
use crate::java_numbers::JNumber;
use crate::wasmengine::EngineHandle;
use crate::wasmlinker::JWasmtimeLinker;
use crate::wasmlinker::LinkerHandle;
use crate::wasmmemory::JWasmtimeLocalMemory;
use crate::wasmmodule::JWasmtimeModule;
use crate::wasmmodule::ModuleHandle;
use crate::wasmstore::JWasmtimeStore;
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

    pub unsafe fn as_ref(&self) -> &Instance {
        unsafe { &*self.0 }
    }

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
        extern fn close_instance(instance: InstanceHandle, store: StoreHandle,),
        extern fn run_wasm_func(store: StoreHandle, instance: InstanceHandle, name: JString, parameters: JObject[]) -> JObject[],
        extern fn get_function_reference(store: StoreHandle,instance: InstanceHandle, name: JString) -> JWasmtimeFuncRef,
    }
}

impl JWasmtimeInstanceNativeInterface for JWasmtimeInstanceAPI {
    type Error = jni::errors::Error;

    fn close_instance<'local>(
        _env: &mut ::jni::Env<'local>,
        _this: JWasmtimeInstance<'local>,
        instance: InstanceHandle,
        store: StoreHandle,
    ) -> ::std::result::Result<(), Self::Error> {
        debug!("Closing Instance");
        // Remove the instance from the store map
        unsafe { store.as_ref() }.data_mut().instance = None;

        drop(unsafe { instance.into_box() });
        debug!("Instance closed successfully");
        Ok(())
    }

    fn run_wasm_func<'local>(
        env: &mut ::jni::Env<'local>,
        _this: JWasmtimeInstance<'local>,
        store: StoreHandle,
        instance: InstanceHandle,
        name: ::jni::objects::JString<'local>,
        parameters: ::jni::objects::JObjectArray<'local>,
    ) -> ::std::result::Result<::jni::objects::JObjectArray<'local>, Self::Error> {
        let instance = unsafe { instance.as_ref() };
        let name = name.to_string();
        let s = unsafe { store.as_ref() };

        let result = match instance.get_func(&mut *s, &name) {
            Some(func) => {
                let s = unsafe { store.as_ref() };
                let param_types: Vec<wasmtime::ValType> = func.ty(&mut *s).params().collect();
                let result_types: Vec<wasmtime::ValType> = func.ty(&mut *s).results().collect();
                let args = convert_java_array_to_val_vector(env, store, parameters, &param_types)?;

                let result_len = result_types.len();
                let mut results = vec![Val::I64(0); result_len];
                match func.call(unsafe { store.as_ref() }, &args, &mut results) {
                    Ok(()) => {
                        debug!("Successfully called function {}", name);
                        convert_val_vector_to_java_array(env, store, &results)?
                    }
                    Err(e) => {
                        handle_wasmtime_error(env, e)?;
                        empty_array(env, result_len.try_into().unwrap())?
                    }
                }
            }
            None => {
                let msg = format!("No function found with name {}", name);
                let msg = JNIString::from(msg);
                env.throw_new(jni_str!("java/lang/RuntimeException"), msg)?;
                empty_array(env, 0)?
            }
        };
        Ok(result)
    }

    fn create_instance<'local>(
        env: &mut ::jni::Env<'local>,
        this: JWasmtimeInstance<'local>,
        module: ModuleHandle,
        store: StoreHandle,
        linker: LinkerHandle,
    ) -> ::std::result::Result<::jni::sys::jlong, Self::Error> {
        let linker = unsafe { linker.as_ref() };

        let result = match linker.instantiate(unsafe { store.as_ref() }, unsafe { module.as_ref() })
        {
            Ok(i) => InstanceHandle::new(i).into(),
            Err(e) => {
                handle_wasmtime_error(env, e)?;
                0
            }
        };

        // Store a reference to the java instance in the store
        let global_this = env.new_global_ref(this)?;
        unsafe { store.as_ref() }.data_mut().instance = Some(global_this);
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
        let func = instance.get_func(unsafe { store.as_ref() }, &name);

        if let Some(f) = func {
            let func_object = JWasmtimeFuncRef::from_func(env, store, f)?;
            Ok(func_object)
        } else {
            Ok(JWasmtimeFuncRef::null())
        }
    }
}

///
/// Throws a java RuntimeException from a wasmtime::Error
///
pub fn handle_wasmtime_error<'local>(
    env: &mut ::jni::Env<'local>,
    e: wasmtime::Error,
) -> ::std::result::Result<(), jni::errors::Error> {
    let messages: Vec<_> = e.chain().map(|e| e.to_string()).collect();
    let msg = messages.join("\n");
    debug!("Wasmtime error: {}", msg);
    let msg = JNIString::from(msg);
    env.throw_new(jni_str!("java/lang/RuntimeException"), msg)?;
    Ok(())
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
pub fn convert_val_to_java_object<'local>(
    env: &mut ::jni::Env<'local>,
    store: StoreHandle,
    value: &Val,
) -> Result<JObject<'local>, jni::errors::Error> {
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
                    .data(unsafe { store.as_ref() })
                    .and_then(|global| {
                        global.ok_or(wasmtime::Error::msg("failed to unpack externref"))
                    })
                    .and_then(|global| {
                        global
                            .downcast_ref::<ExternRefContainer>()
                            .ok_or(wasmtime::Error::msg("failed to downcast externref"))
                    });

                let value = match global {
                    Ok(g) => {
                        debug!("Successfully unpacked ExternRef value");
                        Some(unsafe { JObject::from_raw(env, g.value.as_obj().as_raw()) })
                    }
                    Err(e) => {
                        error!("Failed to unpack ExternRef value: {}", e);
                        None
                    }
                };
                value
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
pub fn convert_val_vector_to_java_array<'local>(
    env: &mut ::jni::Env<'local>,
    store: StoreHandle,
    values: &[Val],
) -> Result<JObjectArray<'local>, jni::errors::Error> {
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
pub fn convert_java_object_to_val<'local>(
    env: &mut ::jni::Env<'local>,
    store: StoreHandle,
    item: JObject<'local>,
    val_type: &ValType,
) -> Result<Val, jni::errors::Error> {
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
            if ref_type.matches(&RefType::EXTERNREF) {
                debug!("Creating EXTERNREF from any java object");
                let global_item = env.new_global_ref(item)?;
                let container = ExternRefContainer::new(global_item);
                let r = ExternRef::new(unsafe { store.as_ref() }, container)
                    .and_then(|r| Ok(Val::ExternRef(Some(r))));

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
pub fn convert_java_array_to_val_vector<'local>(
    env: &mut ::jni::Env<'local>,
    store: StoreHandle,
    values: JObjectArray,
    param_types: &[wasmtime::ValType],
) -> Result<Vec<Val>, jni::errors::Error> {
    let array_len = values.len(env)?;

    let mut result = Vec::with_capacity(array_len);
    for index in 0..array_len {
        let item = values.get_element(env, index)?;
        let val_type = param_types.get(index).unwrap();
        let val = convert_java_object_to_val(env, store, item, val_type)?;
        result.push(val);
    }
    Ok(result)
}
