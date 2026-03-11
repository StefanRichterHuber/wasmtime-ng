use jni::sys::jint;
use log::{Level, LevelFilter};

use crate::wasmengine::JWasmtimeEngine;

/// Base for custom `log::Log` implementation, which allows delegating log output to the Java runtime.
struct JavaLogContext {
    vm: jni::JavaVM,
    level: Level,
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

        let log_context = JavaLogContext { vm, level: lvl };

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
                    let level = record.level() as jint;
                    let message = env.new_string(format!(
                        "{} {}",
                        record.metadata().target(),
                        record.args()
                    ))?;
                    JWasmtimeEngine::runtime_log(env, level, message)?;

                    Ok(())
                });
        }
    }

    fn flush(&self) {
        // Nothing to do here
    }
}
