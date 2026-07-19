use std::fs;

use jni::bind_java_type;
use jni::jni_str;
use jni::objects::{JClass, JObject, JObjectArray, JString};
use jni::strings::JNIString;
use jni::sys::jsize;
use log::debug;
use wit_parser::{Resolve, Type};

bind_java_type! {
    rust_type = pub JWitParam,
    java_type = "io.github.stefanrichterhuber.witparser.WitParam",

    constructors {
        fn new(name: JString, type_name: JString),
    },
}

bind_java_type! {
    rust_type = pub JWitFunction,
    java_type = "io.github.stefanrichterhuber.witparser.WitFunction",

    constructors {
        fn new(name: JString, params: JObject[], result_type: JString),
    },
}

bind_java_type! {
    rust_type = pub JWitInterface,
    java_type = "io.github.stefanrichterhuber.witparser.WitInterface",

    constructors {
        fn new(name: JString, functions: JObject[]),
    },
}

bind_java_type! {
    rust_type = pub JWitParser,
    java_type = "io.github.stefanrichterhuber.witparser.WitParser",

    native_methods {
        extern static fn parse_wit_file(path: JString) -> JObject[],
    },
}

/// A WIT interface resolved from a single `.wit` file, with only the
/// primitive-typed function signatures this MVP understands.
struct ParsedInterface {
    name: String,
    functions: Vec<ParsedFunction>,
}

struct ParsedFunction {
    name: String,
    /// (parameter name, WIT type keyword, e.g. "u32")
    params: Vec<(String, String)>,
    /// WIT type keyword of the single return value, if any.
    result: Option<String>,
}

/// Maps a `wit_parser::Type` to the lowercase WIT keyword this MVP passes
/// across the JNI boundary as a plain string (the Java side re-derives
/// `WitType` from it). Only primitive types are supported here; anything
/// else (records, variants, lists, resources, type aliases -- all surfaced
/// as `Type::Id`) is out of scope for this first pass.
fn wit_type_name(ty: &Type) -> anyhow::Result<&'static str> {
    Ok(match ty {
        Type::Bool => "bool",
        Type::S8 => "s8",
        Type::U8 => "u8",
        Type::S16 => "s16",
        Type::U16 => "u16",
        Type::S32 => "s32",
        Type::U32 => "u32",
        Type::S64 => "s64",
        Type::U64 => "u64",
        Type::F32 => "f32",
        Type::F64 => "f64",
        Type::Char => "char",
        Type::String => "string",
        other => anyhow::bail!(
            "unsupported WIT type {other:?}: only primitive types are supported so far"
        ),
    })
}

fn parse_wit_source(path: &str) -> anyhow::Result<Vec<ParsedInterface>> {
    let contents =
        fs::read_to_string(path).map_err(|e| anyhow::anyhow!("failed to read {path}: {e}"))?;

    let mut resolve = Resolve::default();
    let package_id = resolve.push_source(path, &contents)?;
    let package = &resolve.packages[package_id];

    let mut interfaces = Vec::new();
    for (iface_name, iface_id) in &package.interfaces {
        let interface = &resolve.interfaces[*iface_id];
        let mut functions = Vec::new();
        for (func_name, function) in &interface.functions {
            let mut params = Vec::new();
            for param in &function.params {
                params.push((param.name.clone(), wit_type_name(&param.ty)?.to_string()));
            }
            let result = match &function.result {
                Some(ty) => Some(wit_type_name(ty)?.to_string()),
                None => None,
            };
            functions.push(ParsedFunction {
                name: func_name.clone(),
                params,
                result,
            });
        }
        interfaces.push(ParsedInterface {
            name: iface_name.clone(),
            functions,
        });
    }
    Ok(interfaces)
}

impl JWitParserNativeInterface for JWitParserAPI {
    type Error = jni::errors::Error;

    fn parse_wit_file<'local>(
        env: &mut ::jni::Env<'local>,
        _class: JClass<'local>,
        path: JString<'local>,
    ) -> ::std::result::Result<JObjectArray<'local>, Self::Error> {
        let path_string: String = path.to_string();
        debug!("Parsing WIT file {path_string}");

        let parsed = match parse_wit_source(&path_string) {
            Ok(parsed) => parsed,
            Err(e) => {
                env.throw_new(
                    jni_str!("java/lang/RuntimeException"),
                    JNIString::from(e.to_string()),
                )?;
                return env.new_object_array(0, jni_str!("java.lang.Object"), JObject::null());
            }
        };

        let interfaces_array = env.new_object_array(
            parsed.len() as jsize,
            jni_str!("io.github.stefanrichterhuber.witparser.WitInterface"),
            JObject::null(),
        )?;

        for (i, iface) in parsed.iter().enumerate() {
            let functions_array = env.new_object_array(
                iface.functions.len() as jsize,
                jni_str!("io.github.stefanrichterhuber.witparser.WitFunction"),
                JObject::null(),
            )?;

            for (j, func) in iface.functions.iter().enumerate() {
                let params_array = env.new_object_array(
                    func.params.len() as jsize,
                    jni_str!("io.github.stefanrichterhuber.witparser.WitParam"),
                    JObject::null(),
                )?;

                for (k, (pname, ptype)) in func.params.iter().enumerate() {
                    let name_j = env.new_string(pname)?;
                    let type_j = env.new_string(ptype)?;
                    let param_obj = JWitParam::new(env, name_j, type_j)?;
                    params_array.set_element(env, k, param_obj)?;
                }

                let func_name_j = env.new_string(&func.name)?;
                let result_j: JString = match &func.result {
                    Some(r) => env.new_string(r)?,
                    None => env.cast_local::<JString>(JObject::null())?,
                };

                let func_obj = JWitFunction::new(env, func_name_j, params_array, result_j)?;
                functions_array.set_element(env, j, func_obj)?;
            }

            let iface_name_j = env.new_string(&iface.name)?;
            let iface_obj = JWitInterface::new(env, iface_name_j, functions_array)?;
            interfaces_array.set_element(env, i, iface_obj)?;
        }

        Ok(interfaces_array)
    }
}
