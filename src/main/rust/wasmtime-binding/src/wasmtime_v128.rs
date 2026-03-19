use crate::java_numbers::JNumber;
use jni::bind_java_type;
use log::debug;
use wasmtime::V128;

bind_java_type! {
    rust_type = pub JV128,
    java_type = io.github.stefanrichterhuber.wasmtimejavang.V128,

    type_map = {
        JNumber =>  "java.lang.Number"
    },

    constructors {
        fn new_from_bytes(parts: jbyte[]),
        fn new_from_shorts(parts: jshort[]),
        fn new_from_ints(parts: jint[]),
        fn new_from_longs(parts: jlong[]),
    },

    methods {
        fn get_bytes() -> jbyte[],
        fn get_shorts() -> jshort[],
        fn get_ints() -> jint[],
        fn get_longs() -> jlong[],
        fn compare_to(other: JV128) -> jint,
    },

    is_instance_of = {
        // With stem: generates as_number() method
        number: JNumber,
    },
}

impl<'local> JV128<'local> {
    ///
    /// Creates an JV128 from a v128 value
    ///
    pub fn from_v128(
        env: &mut ::jni::Env<'local>,
        v128: &V128,
    ) -> Result<JV128<'local>, jni::errors::Error> {
        debug!("Converting V128 value to java V128");
        let value_bytes = v128.as_u128().to_le_bytes();
        let signed_value_bytes = unsafe {
            std::slice::from_raw_parts(value_bytes.as_ptr() as *const i8, value_bytes.len())
        };
        let byte_array = env.new_byte_array(signed_value_bytes.len())?;

        byte_array.set_region(env, 0, signed_value_bytes)?;

        JV128::new_from_bytes(env, &byte_array)
    }

    ///
    /// Creates an JV128 from a byte slice
    ///
    pub fn from_byte_slice(
        env: &mut ::jni::Env<'local>,
        slice: &[i8; 16],
    ) -> Result<JV128<'local>, jni::errors::Error> {
        let byte_array = env.new_byte_array(slice.len())?;

        byte_array.set_region(env, 0, slice)?;
        JV128::new_from_bytes(env, &byte_array)
    }

    pub fn into_byte_slice(
        &self,
        env: &mut ::jni::Env<'local>,
    ) -> Result<[u8; 16], jni::errors::Error> {
        let value_array = self.get_bytes(env)?;
        let elements =
            unsafe { value_array.get_elements(env, jni::objects::ReleaseMode::NoCopyBack)? };
        let u8_array: [u8; 16] = elements
            .iter()
            .map(|&x| x as u8)
            .collect::<Vec<u8>>()
            .try_into()
            .unwrap();

        Ok(u8_array)
    }

    pub fn into_u128(&self, env: &mut ::jni::Env<'local>) -> Result<u128, jni::errors::Error> {
        let u8_array = self.into_byte_slice(env)?;
        Ok(u128::from_le_bytes(u8_array))
    }

    pub fn into_v128(&self, env: &mut ::jni::Env<'local>) -> Result<V128, jni::errors::Error> {
        let value = self.into_u128(env)?;
        Ok(V128::from(value))
    }
}
