use std::fmt::Display;

use jni::{
    bind_java_type,
    objects::{JClass, JThrowable},
    refs::{LoaderContext, Reference},
};
use log::debug;

bind_java_type! {
    rust_type = pub JWasmRuntimeException,
    java_type = "io.github.stefanrichterhuber.wasmtimejavang.WasmRuntimeException",

    constructors {
        pub fn new_with_msg(msg: JString),
        pub fn new_with_msg_and_stack(msg: JString, stack: JString),
        pub fn new_with_msg_and_cause(msg: JString, cause: JThrowable),
        pub fn new_with_msg_and_stack_and_cause(msg: JString, stack: JString, cause: JThrowable),
    },

    methods {
        fn set_wasm_stack(stack: JString)
    },

    is_instance_of = {
        exception: JThrowable,
    },
}

bind_java_type! {
    rust_type = pub JProcExitException,
    java_type = "io.github.stefanrichterhuber.wasmtimejavang.wasip1.ProcExitException",

    type_map {
        JWasmRuntimeException => "io.github.stefanrichterhuber.wasmtimejavang.WasmRuntimeException",
    },

    constructors {
        pub fn new_with_code(code: jint),
    },

    methods {
        pub fn get_code() -> jint,
    },

    is_instance_of = {
        wasmtime_exception: JWasmRuntimeException,
    },
}

/// Wraps a java exception into a std::error::Error
/// compatible struct so it can passed through the wasmtime error structs
#[derive(Debug)]
struct ExceptionWrapper {
    name: String,
    stack: String,
    msg: String,
    exception: jni::refs::Global<jni::objects::JThrowable<'static>>,
}

impl Display for ExceptionWrapper {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}: {}:\n{}", self.name, self.msg, self.stack)
    }
}

impl std::error::Error for ExceptionWrapper {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        None
    }

    fn description(&self) -> &str {
        "description() is deprecated; use Display"
    }

    fn cause(&self) -> Option<&dyn std::error::Error> {
        self.source()
    }
}

///
/// Converts jni::errors::Error to wasmtime::error:Error
/// If the jni::errors::Error is a CaughtJavaException, it wraps the java exception into a ExceptionWrapper and then warp it into a wasmtime::error::Error
/// If not, just create a new wasmtime::error::Error from the error message
///
pub fn convert_jvm_error_to_wasmtime_error(
    jvm_error: jni::errors::Error,
) -> wasmtime::error::Error {
    match jvm_error {
        jni::errors::Error::CaughtJavaException {
            stack,
            exception,
            name,
            msg,
            ..
        } => {
            let w = ExceptionWrapper {
                name,
                stack,
                msg,
                exception,
            };

            wasmtime::error::Error::new(w)
        }
        _ => wasmtime::error::Error::msg(jvm_error.to_string()),
    }
}

///
/// Throws a java Exception from a wasmtime::Error
/// First checks if the wasmtime::Error is actually an ExceptionWrapper
/// If it is, it throws the java exception
/// If it is not, it throws a new java WasmRuntimeException
///
pub fn handle_wasmtime_error<'local>(
    env: &mut ::jni::Env<'local>,
    e: wasmtime::Error,
) -> ::std::result::Result<(), jni::errors::Error> {
    let core_msg = e.to_string();
    let core_msg = env.new_string(core_msg)?;

    let messages: Vec<_> = e.chain().map(|e| e.to_string()).collect();
    let msg = messages.join("\n");
    let jstring_msg = env.new_string(msg.as_str())?;
    let exception_class = JWasmRuntimeException::lookup_class(env, &LoaderContext::None)?;
    let exception_class: &JClass = exception_class.as_ref();

    // First check if the error is a ExceptionWrapper
    if let Some(w) = e.downcast_ref::<ExceptionWrapper>() {
        // Since this is on the top of the error trace,
        // just add the wasm stack to the exception and rethrow it
        debug!("ExceptionWrapper found in wasmtime cause");

        if env.is_instance_of(&w.exception, exception_class)? {
            let exception = env.new_local_ref(w.exception.as_obj())?;
            let exception = env.cast_local::<JWasmRuntimeException>(exception)?;
            exception.set_wasm_stack(env, jstring_msg)?;

            env.throw(&*exception.as_exception())?;
            return Ok(());
        } else {
            let cause = env.new_local_ref(w.exception.as_obj())?;
            let cause = env.cast_local::<JThrowable>(cause)?;
            let exception = JWasmRuntimeException::new_with_msg_and_stack_and_cause(
                env,
                core_msg,
                jstring_msg,
                cause,
            )?;
            env.throw(&*exception.as_exception())?;
            return Ok(());
        }
    }

    // Then check if there is some Java Exception in the stack trace
    for c in e.chain() {
        if let Some(w) = c.downcast_ref::<ExceptionWrapper>() {
            // We create a new exception with this one as cause
            debug!("ExceptionWrapper found in wasmtime stack trace");
            let cause = env.new_local_ref(w.exception.as_obj())?;
            let cause = env.cast_local::<JThrowable>(cause)?;
            let exception = JWasmRuntimeException::new_with_msg_and_stack_and_cause(
                env,
                core_msg,
                jstring_msg,
                cause,
            )?;

            env.throw(&*exception.as_exception())?;
            return Ok(());
        }
    }

    // No exception found in the stack, just create a new one
    let exception = JWasmRuntimeException::new_with_msg_and_stack(env, core_msg, jstring_msg)?;
    env.throw(&*exception.as_exception())?;
    Ok(())
}
