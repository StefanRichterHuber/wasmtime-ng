use jni::{
    JValue, jni_sig, jni_str,
    objects::{JClass, JStaticMethodID, JValueOwned},
    refs::Global,
    signature::{Primitive, ReturnType},
    sys::jint,
};
use log::{Level, LevelFilter};

/// Base for custom `log::Log` implementation, which allows delegating log output to the Java runtime.
struct JavaLogContext {
    vm: jni::JavaVM,
    level: Level,
    engine_class: Global<JClass<'static>>,
    runtime_log_method_id: JStaticMethodID,
}

pub fn init_logging<'local>(
    env: &mut ::jni::Env<'local>,
    level: jint,
) -> ::std::result::Result<(), jni::errors::Error> {
    if level > 0 {
        let vm = env.get_java_vm()?;

        let lvl = match level {
            1 => Level::Error,
            2 => Level::Warn,
            3 => Level::Info,
            4 => Level::Debug,
            5 => Level::Trace,
            _ => Level::Error,
        };
        let filter = match level {
            1 => LevelFilter::Error,
            2 => LevelFilter::Warn,
            3 => LevelFilter::Info,
            4 => LevelFilter::Debug,
            5 => LevelFilter::Trace,
            _ => LevelFilter::Error,
        };

        let engine_class = env.find_class(jni_str!(
            "io/github/stefanrichterhuber/wasmtimejavang/WasmtimeEngine"
        ))?;
        let engine_class_global = env.new_global_ref(engine_class)?;

        let method_id: JStaticMethodID = env.get_static_method_id(
            &engine_class_global,
            jni_str!("runtimeLog"),
            jni_sig!((jint, JString) -> void),
        )?;

        let log_context = JavaLogContext {
            vm,
            level: lvl,
            engine_class: engine_class_global,
            runtime_log_method_id: method_id,
        };

        log::set_boxed_logger(Box::new(log_context))
            .map(|()| log::set_max_level(filter))
            .unwrap();
        log::debug!("Native logging redirect to java log4j initialized");
    }
    Ok(())
}

/// Implementation of `log::Log` for JavaLogContext. All log messages are passed to the corresponding java method.
impl log::Log for JavaLogContext {
    fn enabled(&self, metadata: &log::Metadata) -> bool {
        if metadata.target().starts_with("jni::") {
            // Tracing from the jni crate itself won't be forwarded to java because it creates an endless loop
            false
        } else {
            metadata.level() <= self.level
        }
    }

    fn log(&self, record: &log::Record) {
        if self.enabled(record.metadata()) {
            let _result: std::result::Result<(), jni::errors::Error> =
                self.vm.attach_current_thread(|env| {
                    let level_int = record.level() as i32;
                    let message = format!("{} {}", record.metadata().target(), record.args());

                    if let Ok(message_string) = env.new_string(message) {
                        let args = [
                            JValue::Int(level_int).as_jni(),
                            JValue::Object(&message_string).as_jni(),
                        ];
                        unsafe {
                            let method_id = self.runtime_log_method_id;
                            let _res: JValueOwned = env.call_static_method_unchecked(
                                &self.engine_class,
                                method_id,
                                ReturnType::Primitive(Primitive::Void),
                                &args,
                            )?;
                        }
                    }
                    Ok(())
                });
        }
    }

    fn flush(&self) {
        // Nothing to do here
    }
}
