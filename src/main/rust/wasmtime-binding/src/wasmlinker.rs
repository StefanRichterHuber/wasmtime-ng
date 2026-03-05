use crate::wasmengine::EngineHandle;
use crate::wasmstore::StoreHandle;
use jni::{
    JValue, bind_java_type, jni_sig, jni_str,
    objects::{JList, JLongArray, JMap, JObject, JString},
    refs::Global,
    sys::jlong,
};
use wasmtime::{Func, FuncType, Linker, Val, ValType};

#[repr(transparent)]
#[derive(Copy, Clone)]
struct LinkerHandle(*mut Linker<Global<JMap<'static>>>);

#[allow(dead_code)]
impl LinkerHandle {
    pub fn new(linker: Linker<Global<JMap<'static>>>) -> Self {
        let boxed = Box::new(linker);
        LinkerHandle(Box::into_raw(boxed))
    }

    pub unsafe fn as_ref(&self) -> &mut Linker<Global<JMap<'static>>> {
        unsafe { &mut *self.0 }
    }

    pub unsafe fn into_box(self) -> Box<Linker<Global<JMap<'static>>>> {
        unsafe { Box::from_raw(self.0 as *mut Linker<Global<JMap<'static>>>) }
    }
}

impl From<LinkerHandle> for jlong {
    fn from(linker: LinkerHandle) -> Self {
        linker.0 as jlong
    }
}

bind_java_type! {
    rust_type = JWasmtimeLinker,
    java_type = "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeLinker",

    type_map = {
        unsafe EngineHandle => long,
        unsafe LinkerHandle => long,
        unsafe StoreHandle => long,

    },

    constructors {
        fn new(engine: EngineHandle),
    },

    methods {

    },

    native_methods {
        extern fn create_linker(engine: EngineHandle) -> jlong,
        extern fn close_linker(linker: LinkerHandle),
        extern fn define_function(engine: EngineHandle, store:StoreHandle, func: io.github.stefanrichterhuber.wasmtimejavang.WasmFunction, name:JString, parameters: JList, return_types: JList)
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
        println!("Created Linker");
        Ok(result.into())
    }

    fn close_linker<'local>(
        _env: &mut ::jni::Env<'local>,
        _this: JWasmtimeLinker<'local>,
        linker: LinkerHandle,
    ) -> ::std::result::Result<(), Self::Error> {
        println!("Linker closed");
        drop(unsafe { linker.into_box() });
        Ok(())
    }

    fn define_function<'local>(
        env: &mut ::jni::Env<'local>,
        _this: JWasmtimeLinker<'local>,
        engine: EngineHandle,
        store: StoreHandle,
        func: JObject,
        name: ::jni::objects::JString<'local>,
        parameters: ::jni::objects::JList<'local>,
        return_types: ::jni::objects::JList<'local>,
    ) -> ::std::result::Result<(), Self::Error> {
        let params = convert_val_type_enum_list_to_vec(env, parameters)?;
        let results = convert_val_type_enum_list_to_vec(env, return_types)?;
        let signature = FuncType::new(unsafe { engine.as_ref() }, params, results);

        // Create a global reference to the function
        let func = env.new_global_ref(func)?;
        let jvm = env.get_java_vm()?;

        let dynamic_func = Func::new(
            unsafe { store.as_ref() },
            signature,
            move |_caller, args, returns| {
                println!("Dynamic call triggered with {} arguments!", args.len());
                let result: std::result::Result<Vec<i64>, jni::errors::Error> = jvm
                    .attach_current_thread(|env| {
                        // Convert the args to a JLongArray
                        let args_array = env.new_long_array(args.len())?;

                        let mut args_values = Vec::with_capacity(args.len());
                        for arg in args.iter() {
                            match arg {
                                Val::I32(v) => args_values.push(*v as jlong),
                                Val::I64(v) => args_values.push(*v),
                                _ => println!("  Arg [{:?}]: Other type", arg),
                            }
                        }
                        args_array.set_region(env, 0, args_values.as_slice())?;

                        // TODO handle call results
                        let call_result = env.call_method(
                            &func,
                            jni_str!("call"),
                            jni_sig!( ( jlong[]) -> jlong[]),
                            &[JValue::Object(&args_array)],
                        )?;

                        env.exception_catch()?;
                        let result = call_result.l()?;
                        let result_array = JLongArray::cast_local(env, result)?;

                        let result_array_len = result_array.len(env)?;
                        let mut buffer = vec![0i64; result_array_len as usize];
                        result_array.get_region(env, 0, &mut buffer)?;

                        println!("Dynamic call was successfull: {:?}", buffer);

                        Ok(buffer)
                    });

                match result {
                    Ok(values) => {
                        for (i, v) in values.iter().enumerate() {
                            returns[i] = Val::I64(*v)
                        }
                    }
                    Err(_e) => {
                        // TODO handle error
                    }
                }
                Ok(())
            },
        );

        // 3. Add to Linker
        let mut linker = Linker::new(unsafe { engine.as_ref() });
        linker
            .define(
                unsafe { store.as_ref() },
                "env",
                &name.to_string(),
                dynamic_func,
            )
            .unwrap();
        Ok(())
    }
}

fn convert_val_type_enum_list_to_vec<'local>(
    env: &mut ::jni::Env<'local>,
    values: JList,
) -> Result<Vec<ValType>, jni::errors::Error> {
    let iterator = env
        .call_method(
            values,
            jni_str!("iterator"),
            jni_sig!( () -> java.util.Iterator),
            &[],
        )?
        .l()?;

    let mut result = Vec::new();

    // Call the iterator on the list to fetch each enum
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

        // Fore each enum we get the name and convert it to a string
        let enum_value_name = env
            .call_method(
                &item,
                jni_str!("name"),
                jni_sig!( () -> java.lang.String),
                &[],
            )?
            .l()?;
        let enum_value_name = JString::cast_local(env, enum_value_name)?;
        let enum_value_name: String = enum_value_name.to_string();

        // Then conver the name of the enum into the corresponding ValType
        let value: ValType = match enum_value_name.as_str() {
            "I32" => ValType::I32,
            "I64" => ValType::I64,
            "F64" => ValType::F64,
            "V128" => ValType::V128,
            _ => ValType::I32,
        };

        result.push(value);
    }

    Ok(result)
}
