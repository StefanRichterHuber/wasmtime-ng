use crate::wasmengine::EngineHandle;
use crate::wasmlinker::LinkerHandle;
use crate::wasmmodule::ModuleHandle;
use crate::wasmstore::StoreHandle;
use jni::{
    JValue, bind_java_type, jni_sig, jni_str,
    objects::{JList, JString},
    strings::JNIString,
    sys::jlong,
};
use log::{debug, error};
use wasmtime::{Instance, Val};

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
        extern fn close_instance(instance: InstanceHandle),
        extern fn run_wasm_func(store: StoreHandle, instance: InstanceHandle, name: JString, parameters: JList) -> JList,
    }
}

impl JWasmtimeInstanceNativeInterface for JWasmtimeInstanceAPI {
    type Error = jni::errors::Error;

    fn close_instance<'local>(
        _env: &mut ::jni::Env<'local>,
        _this: JWasmtimeInstance<'local>,
        instance: InstanceHandle,
    ) -> ::std::result::Result<(), Self::Error> {
        debug!("Instance closed");
        drop(unsafe { instance.into_box() });
        Ok(())
    }

    fn run_wasm_func<'local>(
        env: &mut ::jni::Env<'local>,
        _this: JWasmtimeInstance<'local>,
        store: StoreHandle,
        instance: InstanceHandle,
        name: ::jni::objects::JString<'local>,
        parameters: ::jni::objects::JList<'local>,
    ) -> ::std::result::Result<::jni::objects::JList<'local>, Self::Error> {
        let instance = unsafe { instance.as_ref() };
        let name = name.to_string();
        let s = unsafe { store.as_ref() };
        let args = convert_val_list_to_vec(env, parameters)?;

        let result = match instance.get_func(s, &name) {
            Some(func) => {
                let mut results = Vec::new();
                match func.call(unsafe { store.as_ref() }, &args, &mut results) {
                    Ok(()) => {
                        debug!("Successfully called function {}", name);
                        convert_val_vec_to_list(env, &results)?
                    }
                    Err(e) => {
                        handle_wasmtime_error(env, e)?;
                        empty_list(env)?
                    }
                }
            }
            None => {
                let msg = format!("No function found with name {}", name);
                let msg = JNIString::from(msg);
                env.throw_new(jni_str!("java/lang/RuntimeException"), msg)?;
                empty_list(env)?
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
        let java_map = unsafe { store.as_ref() }.data_mut();
        let key = JString::from_str(env, "__instance")?;
        env.call_method(
            java_map,
            jni_str!("put"),
            jni_sig!((java.lang.Object, java.lang.Object) -> java.lang.Object),
            &[JValue::Object(&key), JValue::Object(&this.0)],
        )?;

        debug!("Created Instance");
        Ok(result)
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

pub fn empty_list<'local>(
    env: &mut ::jni::Env<'local>,
) -> Result<JList<'local>, jni::errors::Error> {
    let list_class = env.find_class(jni_str!("java/util/ArrayList"))?;
    let list_obj = env.new_object(list_class, &jni_sig!(() -> void), &[])?;
    let list_obj = JList::cast_local(env, list_obj)?;

    Ok(list_obj)
}

pub fn convert_val_vec_to_list<'local>(
    env: &mut ::jni::Env<'local>,
    values: &[Val],
) -> Result<JList<'local>, jni::errors::Error> {
    let len = values.len();
    let list_class = env.find_class(jni_str!("java/util/ArrayList"))?;
    let list_obj = env.new_object(
        list_class,
        &jni_sig!((jint) -> void),
        &[JValue::Int(len.try_into().unwrap())],
    )?;
    let list_obj = JList::cast_local(env, list_obj)?;

    let long_class = env.find_class(jni_str!("java/lang/Long"))?;
    for v in values.iter() {
        // First create Long object
        let long_object = env
            .call_static_method(
                &long_class,
                jni_str!("valueOf"),
                jni_sig!( (jlong) -> java.lang.Long),
                &[JValue::Long(v.unwrap_i64())],
            )?
            .l()?;

        env.call_method(
            &list_obj,
            jni_str!("add"),
            jni_sig!( (java.lang.Object) -> jboolean),
            &[JValue::Object(&long_object)],
        )?;
    }

    Ok(list_obj)
}

pub fn convert_val_list_to_vec<'local>(
    env: &mut ::jni::Env<'local>,
    values: JList,
) -> Result<Vec<Val>, jni::errors::Error> {
    let iterator = env
        .call_method(
            values,
            jni_str!("iterator"),
            jni_sig!( () -> java.util.Iterator),
            &[],
        )?
        .l()?;

    let mut result = Vec::new();

    // Call the iterator on the list to fetch each value
    while env
        .call_method(
            &iterator,
            jni_str!("hasNext"),
            jni_sig!( () -> jboolean),
            &[],
        )?
        .z()?
    {
        let item = env
            .call_method(
                &iterator,
                jni_str!("next"),
                jni_sig!( () -> java.lang.Object),
                &[],
            )?
            .l()?;

        let long_value = env
            .call_method(item, jni_str!("longValue"), jni_sig!( () ->jlong), &[])?
            .j()?; // .j() extracts the jlong/i64 from the JValue return wrapper

        result.push(Val::I64(long_value));
    }

    Ok(result)
}
