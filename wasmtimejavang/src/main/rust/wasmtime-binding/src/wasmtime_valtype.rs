use jni::{bind_java_type, objects::JList, refs::IntoAuto};
use log::warn;
use wasmtime::{RefType, ValType};

bind_java_type! {
    rust_type = pub JValType,
    java_type = "io.github.stefanrichterhuber.wasmtimejavang.ValType",

    methods {
       fn name() -> JString,
       static fn values() -> JValType[],
    }
}

impl<'local> JValType<'local> {
    ///
    /// Converts a JValType into a wasmtime::ValType
    ///
    pub fn into_val_type(
        &self,
        env: &mut ::jni::Env<'local>,
    ) -> Result<Option<ValType>, jni::errors::Error> {
        let enum_value_name = self.name(env)?.to_string();

        // Then convert the name of the enum into the corresponding ValType
        let value: ValType = match enum_value_name.as_str() {
            "I32" => ValType::I32,
            "I64" => ValType::I64,
            "F32" => ValType::F32,
            "F64" => ValType::F64,
            "V128" => ValType::V128,
            "Ref" => ValType::Ref(RefType::EXTERNREF),
            _ => return Ok(None),
        };

        Ok(Some(value))
    }

    ///
    /// Converts a wasmtime::ValType into a JValType
    ///
    pub fn from_val_type(
        env: &mut ::jni::Env<'local>,
        val: ValType,
    ) -> Result<Option<Self>, jni::errors::Error> {
        let string_name = match val {
            ValType::I32 => "I32",
            ValType::I64 => "I64",
            ValType::F32 => "F32",
            ValType::F64 => "F64",
            ValType::V128 => "V128",
            ValType::Ref(ref_type) => {
                if ref_type.matches(&RefType::EXTERNREF) {
                    "Ref"
                } else {
                    return Ok(None);
                }
            }
        };

        let values = Self::values(env)?;
        for index in 0..values.len(env)? {
            let item = values.get_element(env, index)?;
            let name = item.name(env)?.to_string();

            if name == string_name {
                return Ok(Some(item));
            }
        }

        Ok(None)
    }
}

///
/// Converts the list of java ValType enums to a vec of rust ValTyp enums
///
pub fn convert_val_type_enum_list_to_vec<'local>(
    env: &mut ::jni::Env<'local>,
    values: JList,
) -> Result<Vec<ValType>, jni::errors::Error> {
    let iterator = values.iter(env)?;

    let mut result = Vec::new();

    // Call the iterator on the list to fetch each enum
    while let Some(item) = iterator.next(env)? {
        let item = env.cast_local::<JValType>(item)?;
        let item = item.auto();

        match item.into_val_type(env)? {
            Some(value) => result.push(value),
            None => {
                warn!("Unsupported JValType {} -> using I32", item.name(env)?);
                result.push(ValType::I32);
            }
        };
    }

    Ok(result)
}
