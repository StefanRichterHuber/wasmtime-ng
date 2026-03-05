use jni::sys::jlong;
use wasmtime::Instance;

#[repr(transparent)]
#[derive(Copy, Clone)]
struct InstanceHandle(*const Instance);

#[allow(dead_code)]
impl InstanceHandle {
    pub fn new(instance: Instance) -> Self {
        let boxed = Box::new(instance);
        InstanceHandle(Box::into_raw(boxed))
    }

    unsafe fn as_ref(&self) -> &Instance {
        unsafe { &*self.0 }
    }

    pub unsafe fn into_box(self) -> Box<Instance> {
        unsafe { Box::from_raw(self.0 as *mut Instance) }
    }
}

impl From<InstanceHandle> for jlong {
    fn from(handle: InstanceHandle) -> Self {
        handle.0 as jlong
    }
}
