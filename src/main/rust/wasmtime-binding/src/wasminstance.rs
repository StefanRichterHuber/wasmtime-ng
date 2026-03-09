use crate::wasmlinker::LinkerHandle;
use crate::wasmmodule::ModuleHandle;
use crate::wasmstore::StoreHandle;
use crate::{wasmengine::EngineHandle, wasmtimefunc::FuncHandle};
use jni::objects::JPrimitiveArray;
use jni::refs::Global;
use jni::sys::jbyte;
use jni::{
    JValue, bind_java_type, jni_sig, jni_str,
    objects::{JObject, JObjectArray},
    strings::JNIString,
    sys::{jlong, jsize},
};
use log::{debug, error, warn};
use wasmtime::{ExternRef, Func, Instance, RefType, V128, Val};

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
    rust_type = JWasmtimeInstance,
    java_type = "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeInstance",

    type_map = {
        unsafe EngineHandle => long,
        unsafe StoreHandle => long,
        unsafe ModuleHandle => long,
        unsafe LinkerHandle => long,
        unsafe InstanceHandle => long,

    },

    constructors {
        fn new(store: StoreHandle, module: ModuleHandle, linker: LinkerHandle),
    },

    methods {

    },

    native_methods {
        extern fn create_instance(module: ModuleHandle, store: StoreHandle, linker: LinkerHandle ) -> jlong,
        extern fn close_instance(instance: InstanceHandle, store: StoreHandle,),
        extern fn run_wasm_func(store: StoreHandle, instance: InstanceHandle, name: JString, parameters: JObject[]) -> JObject[],
        extern fn get_function_reference(store: StoreHandle,instance: InstanceHandle, name: JString) -> JObject,
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
        let global_this = env.new_global_ref(this.0)?;
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
    ) -> ::std::result::Result<::jni::objects::JObject<'local>, Self::Error> {
        let instance = unsafe { instance.as_ref() };
        let name = name.to_string();
        let func = instance.get_func(unsafe { store.as_ref() }, &name);

        if let Some(f) = func {
            let func_object = convert_func_to_func_ref(env, store, f)?;
            Ok(func_object)
        } else {
            Ok(JObject::null())
        }
    }
}

pub fn handle_wasmtime_error<'local>(
    env: &mut ::jni::Env<'local>,
    e: wasmtime::Error,
) -> ::std::result::Result<(), jni::errors::Error> {
    let messages: Vec<_> = e.chain().map(|e| e.to_string()).collect();
    let msg = messages.join("\n");
    error!("Wasmtime error: {}", msg);
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
        let obj: Option<jni::objects::JObject> = match v {
            Val::I32(v) => {
                let class = env.find_class(jni_str!("java/lang/Integer"))?;
                Some(
                    env.call_static_method(
                        &class,
                        jni_str!("valueOf"),
                        jni_sig!( (jint) -> java.lang.Integer),
                        &[JValue::Int(*v)],
                    )?
                    .l()?,
                )
            }
            Val::I64(v) => {
                let class = env.find_class(jni_str!("java/lang/Long"))?;
                Some(
                    env.call_static_method(
                        &class,
                        jni_str!("valueOf"),
                        jni_sig!( (jlong) -> java.lang.Long),
                        &[JValue::Long(*v)],
                    )?
                    .l()?,
                )
            }
            Val::F32(v) => {
                let class = env.find_class(jni_str!("java/lang/Float"))?;
                Some(
                    env.call_static_method(
                        &class,
                        jni_str!("valueOf"),
                        jni_sig!( (jfloat) -> java.lang.Float),
                        &[JValue::Float(f32::from_bits(*v))],
                    )?
                    .l()?,
                )
            }
            Val::F64(v) => {
                let class = env.find_class(jni_str!("java/lang/Double"))?;
                Some(
                    env.call_static_method(
                        &class,
                        jni_str!("valueOf"),
                        jni_sig!( (jdouble) -> java.lang.Double),
                        &[JValue::Double(f64::from_bits(*v))],
                    )?
                    .l()?,
                )
            }
            Val::V128(v128) => {
                debug!("Converting V128 value to java V128");
                let value_bytes = v128.as_u128().to_le_bytes();
                let signed_value_bytes: &[i8] = unsafe {
                    std::slice::from_raw_parts(value_bytes.as_ptr() as *const i8, value_bytes.len())
                };
                let byte_array = env.new_byte_array(value_bytes.len())?;

                byte_array.set_region(env, 0, &signed_value_bytes)?;

                let class =
                    env.find_class(jni_str!("io/github/stefanrichterhuber/wasmtimejavang/V128"))?;

                let v128_object = env.new_object(
                    class,
                    jni_sig!((jbyte[]) -> void),
                    &[JValue::Object(&byte_array)],
                )?;

                Some(v128_object)
            }
            Val::FuncRef(func) => {
                if let Some(f) = func {
                    let func_object = convert_func_to_func_ref(env, store, *f)?;
                    Some(func_object)
                } else {
                    None
                }
            }
            Val::ExternRef(rooted) => {
                if let Some(reference) = rooted {
                    let global = reference.data(unsafe { store.as_ref() }).unwrap().unwrap();
                    let global = global.downcast_ref::<ExternRefContainer>().unwrap();

                    let value = unsafe { JObject::from_raw(env, global.value.as_obj().as_raw()) };
                    Some(value)
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

        if let Some(obj_ref) = obj {
            array.set_element(env, index, obj_ref)?;
        } else {
            array.set_element(env, index, JObject::null())?;
        }
    }

    Ok(array)
}

///
/// Converts a wasm func to a java WasmtimeFuncRef
///
pub fn convert_func_to_func_ref<'local>(
    env: &mut ::jni::Env<'local>,
    store: StoreHandle,
    func: Func,
) -> Result<JObject<'local>, jni::errors::Error> {
    debug!("Converting wasm type 'FuncRef' to java type 'WasmtimeFuncRef'");
    let handle = FuncHandle::new(func);

    let class = env.find_class(jni_str!(
        "io/github/stefanrichterhuber/wasmtimejavang/internal/WasmtimeFuncRef"
    ))?;
    let func_object = env.new_object(
        &class,
        jni_sig!((jlong, jlong) -> void),
        &[JValue::Long(handle.into()), JValue::Long(store.into())],
    )?;
    Ok(func_object)
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

    let mut result = Vec::new();
    for index in 0..array_len {
        let item = values.get_element(env, index)?;
        let val_type = param_types.get(index).unwrap_or(&wasmtime::ValType::I64);
        let val = match val_type {
            wasmtime::ValType::I32 => {
                let v = env
                    .call_method(&item, jni_str!("intValue"), jni_sig!( () ->jint), &[])?
                    .i()?;
                Val::I32(v)
            }
            wasmtime::ValType::I64 => {
                let v = env
                    .call_method(&item, jni_str!("longValue"), jni_sig!( () ->jlong), &[])?
                    .j()?;
                Val::I64(v)
            }
            wasmtime::ValType::F32 => {
                let v = env
                    .call_method(&item, jni_str!("floatValue"), jni_sig!( () ->jfloat), &[])?
                    .f()?;
                Val::F32(v.to_bits())
            }
            wasmtime::ValType::F64 => {
                let v = env
                    .call_method(&item, jni_str!("doubleValue"), jni_sig!( () ->jdouble), &[])?
                    .d()?;
                Val::F64(v.to_bits())
            }
            wasmtime::ValType::V128 => {
                debug!("Converting V128 object to V128 value");
                let raw_value_array = env
                    .call_method(&item, jni_str!("getBytes"), jni_sig!(() -> jbyte[]), &[])?
                    .l()?;

                let value_array = env.cast_local::<JPrimitiveArray<jbyte>>(raw_value_array)?;
                let elements = unsafe {
                    value_array.get_elements(env, jni::objects::ReleaseMode::NoCopyBack)?
                };

                let u8_array: [u8; 16] = elements
                    .iter()
                    .map(|&x| x as u8)
                    .collect::<Vec<u8>>()
                    .try_into()
                    .unwrap();

                let result = u128::from_le_bytes(u8_array);
                Val::V128(V128::from(result))
            }
            wasmtime::ValType::Ref(ref_type) => {
                if ref_type.matches(&RefType::EXTERNREF) {
                    debug!("Creating EXTERNREF from any java object");
                    let global_item = env.new_global_ref(item)?;
                    let container = ExternRefContainer::new(global_item);
                    let r = ExternRef::new(unsafe { store.as_ref() }, container).unwrap();
                    Val::ExternRef(Some(r))
                } else {
                    warn!(
                        "Extern ref type {} conversion from java to wasm not supported",
                        ref_type
                    );
                    Val::I64(0)
                }
            }
        };
        result.push(val);
    }
    Ok(result)
}
