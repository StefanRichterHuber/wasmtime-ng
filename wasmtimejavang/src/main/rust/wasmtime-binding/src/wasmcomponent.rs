use crate::{
    wasmengine::{EngineHandle, JWasmtimeEngine},
    wasmexception::handle_wasmtime_error,
};
use jni::{
    bind_java_type, jni_str,
    objects::{JClass, JObject, JObjectArray},
};
use jni::sys::jlong;
use log::debug;
use wasmtime::component::types::ComponentItem;
use wasmtime::component::Component;

#[repr(transparent)]
#[derive(Copy, Clone)]
pub struct ComponentHandle(*const Component);

#[allow(dead_code)]
impl ComponentHandle {
    pub fn new(component: Component) -> Self {
        let boxed = Box::new(component);
        ComponentHandle(Box::into_raw(boxed))
    }

    /// Returns a reference to the underlying `Component`.
    ///
    /// # Safety
    /// The caller must ensure that the underlying raw pointer is valid and that the `Component` it points to has not been dropped.
    pub unsafe fn as_ref(&self) -> &Component {
        unsafe { &*self.0 }
    }

    /// Converts the raw pointer back into a `Box<Component>`.
    ///
    /// # Safety
    /// The caller must ensure that the underlying raw pointer is valid and has not already been freed.
    /// Calling this method transfers ownership to the returned `Box`, so the raw pointer must not be used afterwards to avoid double-free or use-after-free errors.
    pub unsafe fn into_box(self) -> Box<Component> {
        unsafe { Box::from_raw(self.0 as *mut Component) }
    }
}

impl From<ComponentHandle> for jlong {
    fn from(handle: ComponentHandle) -> Self {
        handle.0 as jlong
    }
}

bind_java_type! {
    rust_type = pub JWasmtimeComponent,
    java_type = "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponent",

    type_map = {
        unsafe EngineHandle => long,
        unsafe ComponentHandle => long,
        JWasmtimeEngine => "io.github.stefanrichterhuber.wasmtimejavang.WasmtimeEngine"
    },

    fields = {
        component_ptr: jlong,
        engine: JWasmtimeEngine,
    },

    native_methods {
        extern fn create_component(engine: EngineHandle, src: JByteBuffer) -> jlong,
        extern static fn close_component(component: ComponentHandle),
        extern fn import_interfaces(component: ComponentHandle) -> JObject[],
        extern fn export_interfaces(component: ComponentHandle) -> JObject[],
    }
}

/// Builds a Java `String[]` from a slice of Rust strings.
fn string_slice_to_java_array<'local>(
    env: &mut ::jni::Env<'local>,
    values: &[String],
) -> ::std::result::Result<JObjectArray<'local>, jni::errors::Error> {
    let array = env.new_object_array(
        values.len() as i32,
        jni_str!("java.lang.String"),
        JObject::null(),
    )?;
    for (index, value) in values.iter().enumerate() {
        let s = env.new_string(value)?;
        array.set_element(env, index, s)?;
    }
    Ok(array)
}


impl JWasmtimeComponentNativeInterface for JWasmtimeComponentAPI {
    type Error = jni::errors::Error;

    fn close_component<'local>(
        _env: &mut ::jni::Env<'local>,
        _class: JClass<'local>,
        component: ComponentHandle,
    ) -> ::std::result::Result<(), Self::Error> {
        debug!("Component closed");
        drop(unsafe { component.into_box() });
        Ok(())
    }

    fn create_component<'local>(
        env: &mut ::jni::Env<'local>,
        _this: JWasmtimeComponent<'local>,
        engine: EngineHandle,
        src: ::jni::objects::JByteBuffer<'local>,
    ) -> ::std::result::Result<::jni::sys::jlong, Self::Error> {
        let address = env.get_direct_buffer_address(&src)?;
        let size = env.get_direct_buffer_capacity(&src)?;

        let script: &[u8] = unsafe { core::slice::from_raw_parts(address, size) };

        let component = match Component::new(unsafe { engine.as_ref() }, script) {
            Ok(component) => ComponentHandle::new(component).into(),
            Err(e) => {
                handle_wasmtime_error(env, e)?;
                0
            }
        };

        debug!("Created component from source buffer");
        Ok(component)
    }

    /// Names of the component's top-level named interface imports (e.g.
    /// `"wasi:io/poll@0.2.6"`). Bare root-level function imports (not
    /// associated with a named interface) are not included.
    fn import_interfaces<'local>(
        env: &mut ::jni::Env<'local>,
        _this: JWasmtimeComponent<'local>,
        component: ComponentHandle,
    ) -> ::std::result::Result<JObjectArray<'local>, Self::Error> {
        let component_ref = unsafe { component.as_ref() };
        let engine = component_ref.engine();
        let ty = component_ref.component_type();
        let names: Vec<String> = ty
            .imports(engine)
            .filter_map(|(name, ext)| match ext.ty {
                ComponentItem::ComponentInstance(_) => Some(name.to_string()),
                _ => None,
            })
            .collect();
        string_slice_to_java_array(env, &names)
    }

    /// Names of the component's top-level named interface exports (e.g.
    /// `"wasi:cli/run@0.2.6"`). Bare root-level function exports (not
    /// associated with a named interface) are not included.
    fn export_interfaces<'local>(
        env: &mut ::jni::Env<'local>,
        _this: JWasmtimeComponent<'local>,
        component: ComponentHandle,
    ) -> ::std::result::Result<JObjectArray<'local>, Self::Error> {
        let component_ref = unsafe { component.as_ref() };
        let engine = component_ref.engine();
        let ty = component_ref.component_type();
        let names: Vec<String> = ty
            .exports(engine)
            .filter_map(|(name, ext)| match ext.ty {
                ComponentItem::ComponentInstance(_) => Some(name.to_string()),
                _ => None,
            })
            .collect();
        string_slice_to_java_array(env, &names)
    }
}
