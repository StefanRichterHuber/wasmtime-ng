use std::collections::HashMap;
use std::sync::Mutex;
use std::sync::OnceLock;

use jni::bind_java_type;
use jni::jni_str;
use jni::objects::{JByteArray, JMap, JObject, JObjectArray, JSet, JString};
use jni::sys::jsize;
use log::warn;
use wasmtime::AsContextMut;
use wasmtime::StoreContextMut;
use wasmtime::component::{ResourceDynamic, ResourceType, Type, Val};

use crate::java_collections::JArrayList;
use crate::java_collections::JLinkedHashSet;
use crate::java_maps::JLinkedHashMap;
use crate::java_numbers::{JBoolean, JCharacter, JDouble, JFloat, JInteger, JLong, JNumber};
use crate::wasmexception::handle_wasmtime_error;
use crate::wasmstore::StoreContent;

bind_java_type! {
    rust_type = pub JWitVariant,
    java_type = "io.github.stefanrichterhuber.wasmtimejavang.component.WitVariant",

    constructors {
        fn new(case_name: JString, value: JObject),
    },

    methods = {
        fn case_name() -> JString,
        fn value() -> JObject,
    },
}

bind_java_type! {
    rust_type = pub JWitEnum,
    java_type = "io.github.stefanrichterhuber.wasmtimejavang.component.WitEnum",

    constructors {
        fn new(name: JString),
    },

    methods = {
        fn name() -> JString,
    },
}

bind_java_type! {
    rust_type = pub JWitResult,
    java_type = "io.github.stefanrichterhuber.wasmtimejavang.component.WitResult",

    constructors {
        fn new(ok: jboolean, value: JObject),
    },

    methods = {
        fn ok() -> jboolean,
        fn value() -> JObject,
    },
}

bind_java_type! {
    rust_type = pub JWitResource,
    java_type = "io.github.stefanrichterhuber.wasmtimejavang.component.WitResource",

    constructors {
        fn new(resource_name: JString, rep: jint, owned: jboolean),
    },

    methods = {
        fn resource_name() -> JString,
        fn rep() -> jint,
        fn owned() -> jboolean,
    },
}

bind_java_type! {
    rust_type = pub JOptional,
    java_type = "java.util.Optional",

    methods = {
        static fn of_nullable(value: JObject) -> JOptional,
        fn is_present() -> jboolean,
        fn get() -> JObject,
    },
}

/// Registry mapping a WIT resource kind name (e.g. "output-stream") to the
/// arbitrary `u32` payload used to build its `ResourceType::host_dynamic`.
/// Resource kind names are treated as process-wide unique identifiers, the
/// same way WASI interface/resource names are globally scoped by spec.
static RESOURCE_PAYLOADS: OnceLock<Mutex<HashMap<String, u32>>> = OnceLock::new();

fn resource_payload_for(resource_name: &str) -> u32 {
    let map_lock = RESOURCE_PAYLOADS.get_or_init(|| Mutex::new(HashMap::new()));
    let mut map = map_lock.lock().unwrap();
    if let Some(payload) = map.get(resource_name) {
        return *payload;
    }
    let payload = map.len() as u32;
    map.insert(resource_name.to_string(), payload);
    payload
}

/// Returns the `ResourceType` to register/match for the given WIT resource
/// kind name, minting a fresh payload the first time a given name is seen.
pub fn resource_type_for(resource_name: &str) -> ResourceType {
    ResourceType::host_dynamic(resource_payload_for(resource_name))
}

/// Converts a `wasmtime::component::Val` into a Java object. See
/// `ComponentFunction` for the documented mapping.
///
/// This is a thin generic entry point: it resolves `store` into a concrete
/// `StoreContextMut` once and delegates to `val_to_java_object_impl` for the
/// (potentially recursive) conversion, to avoid unbounded monomorphization
/// from recursing through ever-more-nested `&mut T` reference types.
pub fn val_to_java_object<'local, T>(
    env: &mut ::jni::Env<'local>,
    store: &mut T,
    value: &Val,
) -> Result<JObject<'local>, jni::errors::Error>
where
    T: AsContextMut<Data = StoreContent>,
{
    let mut ctx = store.as_context_mut();
    val_to_java_object_impl(env, &mut ctx, value)
}

fn val_to_java_object_impl<'local, 'store>(
    env: &mut ::jni::Env<'local>,
    ctx: &mut StoreContextMut<'store, StoreContent>,
    value: &Val,
) -> Result<JObject<'local>, jni::errors::Error> {
    let obj: JObject = match value {
        Val::Bool(v) => JBoolean::value_of(env, *v)?.into(),
        Val::S8(v) => JInteger::value_of(env, *v as i32)?.into(),
        Val::U8(v) => JInteger::value_of(env, *v as i32)?.into(),
        Val::S16(v) => JInteger::value_of(env, *v as i32)?.into(),
        Val::U16(v) => JInteger::value_of(env, *v as i32)?.into(),
        Val::S32(v) => JInteger::value_of(env, *v)?.into(),
        Val::U32(v) => JInteger::value_of(env, *v as i32)?.into(),
        Val::S64(v) => JLong::value_of(env, *v)?.into(),
        Val::U64(v) => JLong::value_of(env, *v as i64)?.into(),
        Val::Float32(v) => JFloat::value_of(env, *v)?.into(),
        Val::Float64(v) => JDouble::value_of(env, *v)?.into(),
        Val::Char(v) => JCharacter::value_of(env, *v as u32 as u16)?.into(),
        Val::String(v) => env.new_string(v)?.into(),
        Val::List(items) => {
            if !items.is_empty() && items.iter().all(|v| matches!(v, Val::U8(_))) {
                list_u8_to_byte_array(env, items)?.into()
            } else {
                list_to_java_list(env, ctx, items)?
            }
        }
        Val::Record(fields) => {
            let map = JLinkedHashMap::new_with_capacity(env, fields.len().try_into().unwrap())?;
            for (name, v) in fields {
                let key = env.new_string(name)?;
                let value = val_to_java_object_impl(env, ctx, v)?;
                map.put(env, &key, &value)?;
            }
            map.into()
        }
        Val::Tuple(items) => {
            let array = env.new_object_array(
                items.len() as jsize,
                jni_str!("java.lang.Object"),
                JObject::null(),
            )?;
            for (index, v) in items.iter().enumerate() {
                let converted = val_to_java_object_impl(env, ctx, v)?;
                array.set_element(env, index, converted)?;
            }
            array.into()
        }
        Val::Variant(case, payload) => {
            let case_name = env.new_string(case)?;
            let value_obj = match payload {
                Some(v) => val_to_java_object_impl(env, ctx, v)?,
                None => JObject::null(),
            };
            JWitVariant::new(env, case_name, value_obj)?.into()
        }
        Val::Enum(name) => {
            let name = env.new_string(name)?;
            JWitEnum::new(env, name)?.into()
        }
        Val::Option(inner) => {
            let value_obj = match inner {
                Some(v) => val_to_java_object_impl(env, ctx, v)?,
                None => JObject::null(),
            };
            JOptional::of_nullable(env, value_obj)?.into()
        }
        Val::Result(inner) => match inner {
            Ok(payload) => {
                let value_obj = match payload {
                    Some(v) => val_to_java_object_impl(env, ctx, v)?,
                    None => JObject::null(),
                };
                JWitResult::new(env, true, value_obj)?.into()
            }
            Err(payload) => {
                let value_obj = match payload {
                    Some(v) => val_to_java_object_impl(env, ctx, v)?,
                    None => JObject::null(),
                };
                JWitResult::new(env, false, value_obj)?.into()
            }
        },
        Val::Flags(names) => {
            let set = JLinkedHashSet::new_with_capacity(env, names.len().try_into().unwrap())?;
            for name in names {
                let name = env.new_string(name)?;
                set.add(env, name)?;
            }
            set.into()
        }
        Val::Resource(resource_any) => {
            let owned = resource_any.owned();
            let dynamic = match resource_any.try_into_resource_dynamic(&mut *ctx) {
                Ok(d) => d,
                Err(e) => {
                    handle_wasmtime_error(env, e)?;
                    return Err(jni::errors::Error::JavaException);
                }
            };
            let rep = dynamic.rep();
            let unknown_name = env.new_string("")?;
            JWitResource::new(env, unknown_name, rep as i32, owned)?.into()
        }
        other => {
            warn!(
                "Unsupported component value kind for value -> java conversion: {:?}",
                other
            );
            JObject::null()
        }
    };
    Ok(obj)
}

fn list_u8_to_byte_array<'local>(
    env: &mut ::jni::Env<'local>,
    items: &[Val],
) -> Result<JByteArray<'local>, jni::errors::Error> {
    let bytes: Vec<u8> = items
        .iter()
        .map(|v| match v {
            Val::U8(b) => *b,
            _ => 0,
        })
        .collect();
    let array = env.new_byte_array(bytes.len())?;
    let signed: &[i8] =
        unsafe { core::slice::from_raw_parts(bytes.as_ptr() as *const i8, bytes.len()) };
    array.set_region(env, 0, signed)?;
    Ok(array)
}

fn list_to_java_list<'local, 'store>(
    env: &mut ::jni::Env<'local>,
    ctx: &mut StoreContextMut<'store, StoreContent>,
    items: &[Val],
) -> Result<JObject<'local>, jni::errors::Error> {
    let list = JArrayList::new_with_capacity(env, items.len().try_into().unwrap())?;
    for v in items {
        let converted = val_to_java_object_impl(env, ctx, v)?;
        list.add(env, &converted)?;
    }
    Ok(list.into())
}

/// Converts a Java object into a `wasmtime::component::Val`, driven by the
/// target component `Type` (needed to disambiguate e.g. record vs map, or
/// which integer width/signedness a plain Java number represents).
///
/// See `val_to_java_object` for why this resolves `store` once and delegates
/// to a concretely-typed recursive implementation.
pub fn java_object_to_val<'local, T>(
    env: &mut ::jni::Env<'local>,
    mut store: T,
    obj: JObject<'local>,
    ty: &Type,
) -> Result<Val, jni::errors::Error>
where
    T: AsContextMut<Data = StoreContent>,
{
    let mut ctx = store.as_context_mut();
    java_object_to_val_impl(env, &mut ctx, obj, ty)
}

fn java_object_to_val_impl<'local, 'store>(
    env: &mut ::jni::Env<'local>,
    ctx: &mut StoreContextMut<'store, StoreContent>,
    obj: JObject<'local>,
    ty: &Type,
) -> Result<Val, jni::errors::Error> {
    if obj.is_null() {
        return Ok(default_val_for(ty));
    }

    let val = match ty {
        Type::Bool => {
            let o = env.cast_local::<JBoolean>(obj)?;
            Val::Bool(o.boolean_value(env)?)
        }
        Type::S8 => Val::S8(env.cast_local::<JNumber>(obj)?.byte_value(env)?),
        Type::U8 => Val::U8(env.cast_local::<JNumber>(obj)?.byte_value(env)? as u8),
        Type::S16 => Val::S16(env.cast_local::<JNumber>(obj)?.short_value(env)?),
        Type::U16 => Val::U16(env.cast_local::<JNumber>(obj)?.short_value(env)? as u16),
        Type::S32 => Val::S32(env.cast_local::<JNumber>(obj)?.int_value(env)?),
        Type::U32 => Val::U32(env.cast_local::<JNumber>(obj)?.int_value(env)? as u32),
        Type::S64 => Val::S64(env.cast_local::<JNumber>(obj)?.long_value(env)?),
        Type::U64 => Val::U64(env.cast_local::<JNumber>(obj)?.long_value(env)? as u64),
        Type::Float32 => Val::Float32(env.cast_local::<JNumber>(obj)?.float_value(env)?),
        Type::Float64 => Val::Float64(env.cast_local::<JNumber>(obj)?.double_value(env)?),
        Type::Char => {
            let o = env.cast_local::<JCharacter>(obj)?;
            let raw = o.char_value(env)? as u32;
            Val::Char(char::from_u32(raw).unwrap_or('\u{FFFD}'))
        }
        Type::String => {
            let s = env.cast_local::<JString>(obj)?;
            Val::String(s.to_string())
        }
        Type::List(list_ty) => {
            let elem_ty = list_ty.ty();
            if matches!(elem_ty, Type::U8) {
                let arr = env.cast_local::<JByteArray>(obj)?;
                let len = arr.len(env)? as usize;
                let mut signed = vec![0i8; len];
                arr.get_region(env, 0, &mut signed)?;
                let items = signed.into_iter().map(|b| Val::U8(b as u8)).collect();
                Val::List(items)
            } else {
                let list = env.cast_local::<jni::objects::JList>(obj)?;
                let iterator = list.iter(env)?;
                let mut items = Vec::new();
                while let Some(item) = iterator.next(env)? {
                    items.push(java_object_to_val_impl(env, ctx, item, &elem_ty)?);
                }
                Val::List(items)
            }
        }
        Type::Record(record_ty) => {
            let map = env.cast_local::<JMap>(obj)?;
            let mut fields = Vec::new();
            for field in record_ty.fields() {
                let key = env.new_string(field.name)?;
                let value = map.get(env, key.as_ref())?.unwrap_or(JObject::null());
                let converted = java_object_to_val_impl(env, ctx, value, &field.ty)?;
                fields.push((field.name.to_string(), converted));
            }
            Val::Record(fields)
        }
        Type::Tuple(tuple_ty) => {
            let array = env.cast_local::<JObjectArray>(obj)?;
            let mut items = Vec::new();
            for (index, elem_ty) in tuple_ty.types().enumerate() {
                let item = array.get_element(env, index)?;
                items.push(java_object_to_val_impl(env, ctx, item, &elem_ty)?);
            }
            Val::Tuple(items)
        }
        Type::Variant(variant_ty) => {
            let variant = env.cast_local::<JWitVariant>(obj)?;
            let case_name = variant.case_name(env)?.to_string();
            let payload_obj = variant.value(env)?;
            let mut payload = None;
            for case in variant_ty.cases() {
                if case.name == case_name {
                    if let Some(case_ty) = case.ty {
                        payload = Some(Box::new(java_object_to_val_impl(
                            env,
                            ctx,
                            payload_obj,
                            &case_ty,
                        )?));
                    }
                    break;
                }
            }
            Val::Variant(case_name, payload)
        }
        Type::Enum(_) => {
            let e = env.cast_local::<JWitEnum>(obj)?;
            Val::Enum(e.name(env)?.to_string())
        }
        Type::Option(option_ty) => {
            let optional = env.cast_local::<JOptional>(obj)?;
            if optional.is_present(env)? {
                let inner = optional.get(env)?;
                let converted = java_object_to_val_impl(env, ctx, inner, &option_ty.ty())?;
                Val::Option(Some(Box::new(converted)))
            } else {
                Val::Option(None)
            }
        }
        Type::Result(result_ty) => {
            let r = env.cast_local::<JWitResult>(obj)?;
            let is_ok = r.ok(env)?;
            let payload_obj = r.value(env)?;
            if is_ok {
                let payload = match result_ty.ok() {
                    Some(ok_ty) => Some(Box::new(java_object_to_val_impl(
                        env,
                        ctx,
                        payload_obj,
                        &ok_ty,
                    )?)),
                    None => None,
                };
                Val::Result(Ok(payload))
            } else {
                let payload = match result_ty.err() {
                    Some(err_ty) => Some(Box::new(java_object_to_val_impl(
                        env,
                        ctx,
                        payload_obj,
                        &err_ty,
                    )?)),
                    None => None,
                };
                Val::Result(Err(payload))
            }
        }
        Type::Flags(_) => {
            let set = env.cast_local::<JSet>(obj)?;
            let iterator = set.iterator(env)?;
            let mut names = Vec::new();
            while let Some(item) = iterator.next(env)? {
                let s = env.cast_local::<JString>(item)?;
                names.push(s.to_string());
            }
            Val::Flags(names)
        }
        Type::Own(_) | Type::Borrow(_) => {
            let resource = env.cast_local::<JWitResource>(obj)?;
            let resource_name = resource.resource_name(env)?.to_string();
            let rep = resource.rep(env)? as u32;
            let owned = resource.owned(env)?;
            let payload = resource_payload_for(&resource_name);
            let dynamic = if owned {
                ResourceDynamic::new_own(rep, payload)
            } else {
                ResourceDynamic::new_borrow(rep, payload)
            };
            let resource_any = match dynamic.try_into_resource_any(&mut *ctx) {
                Ok(r) => r,
                Err(e) => {
                    handle_wasmtime_error(env, e)?;
                    return Err(jni::errors::Error::JavaException);
                }
            };
            Val::Resource(resource_any)
        }
        other => {
            warn!(
                "Unsupported component type kind for java -> value conversion: {:?}",
                other
            );
            default_val_for(other)
        }
    };
    Ok(val)
}

/// A "zero"-ish `Val` for a given `Type`, used when a java null is provided
/// in a position where a value is expected.
fn default_val_for(ty: &Type) -> Val {
    match ty {
        Type::Bool => Val::Bool(false),
        Type::S8 => Val::S8(0),
        Type::U8 => Val::U8(0),
        Type::S16 => Val::S16(0),
        Type::U16 => Val::U16(0),
        Type::S32 => Val::S32(0),
        Type::U32 => Val::U32(0),
        Type::S64 => Val::S64(0),
        Type::U64 => Val::U64(0),
        Type::Float32 => Val::Float32(0.0),
        Type::Float64 => Val::Float64(0.0),
        Type::Char => Val::Char('\u{0}'),
        Type::String => Val::String(String::new()),
        Type::List(_) => Val::List(Vec::new()),
        Type::Record(record_ty) => Val::Record(
            record_ty
                .fields()
                .map(|f| (f.name.to_string(), default_val_for(&f.ty)))
                .collect(),
        ),
        Type::Tuple(tuple_ty) => {
            Val::Tuple(tuple_ty.types().map(|t| default_val_for(&t)).collect())
        }
        Type::Option(_) => Val::Option(None),
        Type::Flags(_) => Val::Flags(Vec::new()),
        _ => Val::Option(None),
    }
}
