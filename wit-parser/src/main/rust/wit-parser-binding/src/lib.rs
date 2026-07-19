use std::collections::HashSet;
use std::path::Path;

use jni::bind_java_type;
use jni::jni_str;
use jni::objects::{JClass, JObject, JObjectArray, JString};
use jni::strings::JNIString;
use jni::sys::jsize;
use log::debug;
use wit_parser::{Handle, Interface, Resolve, Type, TypeDefKind, WorldItem};

bind_java_type! {
    rust_type = pub JWitParam,
    java_type = "io.github.stefanrichterhuber.witparser.WitParam",

    constructors {
        fn new(name: JString, type_tag: JString, resource_name: JString),
    },
}

bind_java_type! {
    rust_type = pub JWitFunction,
    java_type = "io.github.stefanrichterhuber.witparser.WitFunction",

    constructors {
        fn new(name: JString, params: JObject[], result_tag: JString, result_resource_name: JString),
    },
}

bind_java_type! {
    rust_type = pub JWitInterface,
    java_type = "io.github.stefanrichterhuber.witparser.WitInterface",

    constructors {
        fn new(name: JString, functions: JObject[], resources: JObject[]),
    },
}

bind_java_type! {
    rust_type = pub JWitParser,
    java_type = "io.github.stefanrichterhuber.witparser.WitParser",

    native_methods {
        extern static fn parse_wit_file(path: JString) -> JObject[],
        extern static fn parse_wit_world(directory: JString, world_name: JString) -> JObject[],
    },
}

struct ParsedParam {
    name: String,
    type_tag: String,
    resource_name: Option<String>,
}

struct ParsedFunction {
    name: String,
    params: Vec<ParsedParam>,
    /// (WIT type tag, resource name if the tag is "resource")
    result: Option<(String, Option<String>)>,
}

/// A WIT interface resolved from a `.wit` file/directory or a world, with
/// its own resource types tracked separately from its functions (covers
/// resources with zero methods).
struct ParsedInterface {
    name: String,
    functions: Vec<ParsedFunction>,
    resources: Vec<String>,
}

/// Classifies a `wit_parser::Type` down to the fixed set of shapes
/// `WitTypeKind` (Java side) understands, recursing through named type
/// aliases (`TypeDefKind::Type`) and resource handles. Nested structure
/// (record field types, variant payload types, list/option element types
/// beyond the `list<u8>` special case) isn't tracked -- every non-primitive
/// shape maps to one fixed Java type regardless of what's nested inside it.
/// Returns the shape tag and, for `"resource"`, the bare resource name.
///
/// Deliberately unsupported (this world doesn't use them): the WIT 0.3
/// async surface (`Future`/`Stream`/`Async*` function kinds), `map`, and
/// fixed-length lists.
fn classify(resolve: &Resolve, ty: &Type) -> anyhow::Result<(&'static str, Option<String>)> {
    Ok(match ty {
        Type::Bool => ("bool", None),
        Type::S8 => ("s8", None),
        Type::U8 => ("u8", None),
        Type::S16 => ("s16", None),
        Type::U16 => ("u16", None),
        Type::S32 => ("s32", None),
        Type::U32 => ("u32", None),
        Type::S64 => ("s64", None),
        Type::U64 => ("u64", None),
        Type::F32 => ("f32", None),
        Type::F64 => ("f64", None),
        Type::Char => ("char", None),
        Type::String => ("string", None),
        Type::Id(id) => {
            let def = &resolve.types[*id];
            match &def.kind {
                TypeDefKind::Type(inner) => classify(resolve, inner)?,
                TypeDefKind::Record(_) => ("record", None),
                TypeDefKind::Variant(_) => ("variant", None),
                TypeDefKind::Enum(_) => ("enum", None),
                TypeDefKind::Flags(_) => ("flags", None),
                TypeDefKind::Tuple(_) => ("tuple", None),
                TypeDefKind::Option(_) => ("option", None),
                TypeDefKind::Result(_) => ("result", None),
                TypeDefKind::List(elem) => {
                    let (elem_tag, _) = classify(resolve, elem)?;
                    if elem_tag == "u8" {
                        ("list-u8", None)
                    } else {
                        ("list", None)
                    }
                }
                TypeDefKind::Resource => (
                    "resource",
                    Some(def.name.clone().unwrap_or_else(|| "unknown".to_string())),
                ),
                TypeDefKind::Handle(handle) => {
                    let resource_id = match handle {
                        Handle::Own(id) => *id,
                        Handle::Borrow(id) => *id,
                    };
                    let resource_name = resolve.types[resource_id]
                        .name
                        .clone()
                        .unwrap_or_else(|| "unknown".to_string());
                    ("resource", Some(resource_name))
                }
                other => anyhow::bail!("unsupported WIT type shape: {}", other.as_str()),
            }
        }
        other => anyhow::bail!("unsupported WIT type: {other:?}"),
    })
}

/// Builds the fully-qualified, bare (version-independent) interface name
/// (e.g. `"wasi:io/streams"`) `WasmComponentContext.getProvidedInterfaces()`
/// uses.
fn qualified_interface_name(resolve: &Resolve, interface: &Interface) -> anyhow::Result<String> {
    let bare_name = interface
        .name
        .as_ref()
        .ok_or_else(|| anyhow::anyhow!("interface has no name (inline world interface)"))?;
    let package_id = interface
        .package
        .ok_or_else(|| anyhow::anyhow!("interface '{bare_name}' has no owning package"))?;
    let package = &resolve.packages[package_id];
    Ok(format!(
        "{}:{}/{bare_name}",
        package.name.namespace, package.name.name
    ))
}

fn build_interface(resolve: &Resolve, interface: &Interface) -> anyhow::Result<ParsedInterface> {
    let name = qualified_interface_name(resolve, interface)?;

    let mut functions = Vec::new();
    for (func_name, function) in &interface.functions {
        let mut params = Vec::new();
        for param in &function.params {
            let (tag, resource_name) = classify(resolve, &param.ty)?;
            params.push(ParsedParam {
                name: param.name.clone(),
                type_tag: tag.to_string(),
                resource_name,
            });
        }
        let result = match &function.result {
            Some(ty) => {
                let (tag, resource_name) = classify(resolve, ty)?;
                Some((tag.to_string(), resource_name))
            }
            None => None,
        };
        functions.push(ParsedFunction {
            name: func_name.clone(),
            params,
            result,
        });
    }

    let mut resources = Vec::new();
    for (type_name, type_id) in &interface.types {
        if matches!(resolve.types[*type_id].kind, TypeDefKind::Resource) {
            resources.push(type_name.clone());
        }
    }

    Ok(ParsedInterface {
        name,
        functions,
        resources,
    })
}

/// Resolves everything at `path` (a single WIT file, or a package directory
/// with an optional `deps/` subdirectory) and returns every *named*
/// interface found across all resolved packages -- the default, no-world
/// behavior.
fn parse_wit_source(path: &str) -> anyhow::Result<Vec<ParsedInterface>> {
    let mut resolve = Resolve::default();
    resolve.push_path(Path::new(path))?;

    let mut interfaces = Vec::new();
    for (_, interface) in resolve.interfaces.iter() {
        if interface.name.is_none() {
            continue;
        }
        interfaces.push(build_interface(&resolve, interface)?);
    }
    Ok(interfaces)
}

/// Resolves `dir` and returns the flattened, deduplicated set of interfaces
/// the named world *imports* (transitively, through any `include`s --
/// `Resolve` pre-flattens these, so no manual `include`-walking is needed).
/// Exports are intentionally not surfaced; `WasmComponentContext` only
/// models host-provided imports.
///
/// The world lookup is scoped to the package `push_path` resolves directly
/// from `dir` (as opposed to anything found under `dir/deps`) -- WASI's own
/// sub-packages each define their own world literally named `"imports"`, so
/// matching by bare name alone across every resolved package (including
/// dependencies) is ambiguous.
fn resolve_world_source(dir: &str, world_name: &str) -> anyhow::Result<Vec<ParsedInterface>> {
    let mut resolve = Resolve::default();
    let (top_package_id, _) = resolve.push_path(Path::new(dir))?;

    let world_ids: Vec<_> = resolve
        .worlds
        .iter()
        .filter(|(_, w)| w.name == world_name && w.package == Some(top_package_id))
        .map(|(id, _)| id)
        .collect();
    let world_id = match world_ids.as_slice() {
        [id] => *id,
        [] => anyhow::bail!(
            "no world named '{world_name}' found in the top-level package of {dir}"
        ),
        _ => anyhow::bail!(
            "multiple worlds named '{world_name}' found in the top-level package of {dir}"
        ),
    };
    let world = &resolve.worlds[world_id];

    let mut seen = HashSet::new();
    let mut interfaces = Vec::new();
    for item in world.imports.values() {
        if let WorldItem::Interface { id, .. } = item {
            if !seen.insert(*id) {
                continue;
            }
            let interface = &resolve.interfaces[*id];
            interfaces.push(build_interface(&resolve, interface)?);
        }
    }
    Ok(interfaces)
}

/// Throws a `java.lang.RuntimeException` and returns `Ok(None)` if `result`
/// is an `Err`, otherwise returns `Ok(Some(...))`. Callers must return an
/// empty array immediately on `None` -- the exception is already pending.
fn throw_on_err<'local, T>(
    env: &mut ::jni::Env<'local>,
    result: anyhow::Result<T>,
) -> Result<Option<T>, jni::errors::Error> {
    match result {
        Ok(value) => Ok(Some(value)),
        Err(e) => {
            env.throw_new(
                jni_str!("java/lang/RuntimeException"),
                JNIString::from(e.to_string()),
            )?;
            Ok(None)
        }
    }
}

fn optional_jstring<'local>(
    env: &mut ::jni::Env<'local>,
    value: Option<&str>,
) -> Result<JString<'local>, jni::errors::Error> {
    match value {
        Some(v) => env.new_string(v),
        None => env.cast_local::<JString>(JObject::null()),
    }
}

fn build_interfaces_array<'local>(
    env: &mut ::jni::Env<'local>,
    parsed: &[ParsedInterface],
) -> Result<JObjectArray<'local>, jni::errors::Error> {
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

            for (k, param) in func.params.iter().enumerate() {
                let name_j = env.new_string(&param.name)?;
                let tag_j = env.new_string(&param.type_tag)?;
                let resource_j = optional_jstring(env, param.resource_name.as_deref())?;
                let param_obj = JWitParam::new(env, name_j, tag_j, resource_j)?;
                params_array.set_element(env, k, param_obj)?;
            }

            let func_name_j = env.new_string(&func.name)?;
            let (result_tag_j, result_resource_j) = match &func.result {
                Some((tag, resource_name)) => (
                    env.new_string(tag)?,
                    optional_jstring(env, resource_name.as_deref())?,
                ),
                None => (optional_jstring(env, None)?, optional_jstring(env, None)?),
            };

            let func_obj = JWitFunction::new(
                env,
                func_name_j,
                params_array,
                result_tag_j,
                result_resource_j,
            )?;
            functions_array.set_element(env, j, func_obj)?;
        }

        let resources_array = env.new_object_array(
            iface.resources.len() as jsize,
            jni_str!("java.lang.String"),
            JObject::null(),
        )?;
        for (k, resource_name) in iface.resources.iter().enumerate() {
            let name_j = env.new_string(resource_name)?;
            resources_array.set_element(env, k, name_j)?;
        }

        let iface_name_j = env.new_string(&iface.name)?;
        let iface_obj = JWitInterface::new(env, iface_name_j, functions_array, resources_array)?;
        interfaces_array.set_element(env, i, iface_obj)?;
    }

    Ok(interfaces_array)
}

impl JWitParserNativeInterface for JWitParserAPI {
    type Error = jni::errors::Error;

    fn parse_wit_file<'local>(
        env: &mut ::jni::Env<'local>,
        _class: JClass<'local>,
        path: JString<'local>,
    ) -> ::std::result::Result<JObjectArray<'local>, Self::Error> {
        let path_string: String = path.to_string();
        debug!("Parsing WIT path {path_string}");

        match throw_on_err(env, parse_wit_source(&path_string))? {
            Some(parsed) => build_interfaces_array(env, &parsed),
            None => env.new_object_array(0, jni_str!("java.lang.Object"), JObject::null()),
        }
    }

    fn parse_wit_world<'local>(
        env: &mut ::jni::Env<'local>,
        _class: JClass<'local>,
        directory: JString<'local>,
        world_name: JString<'local>,
    ) -> ::std::result::Result<JObjectArray<'local>, Self::Error> {
        let dir_string: String = directory.to_string();
        let world_name_string: String = world_name.to_string();
        debug!("Resolving world '{world_name_string}' in {dir_string}");

        match throw_on_err(
            env,
            resolve_world_source(&dir_string, &world_name_string),
        )? {
            Some(parsed) => build_interfaces_array(env, &parsed),
            None => env.new_object_array(0, jni_str!("java.lang.Object"), JObject::null()),
        }
    }
}
