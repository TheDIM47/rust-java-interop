use jni::objects::{JClass, JDoubleArray, ReleaseMode};
use jni::sys::{jdouble, jstring};
use jni::JNIEnv;

#[unsafe(no_mangle)]
pub extern "C" fn Java_golf_tweede_JniInterface_doubleToStringRust(
    env: JNIEnv,
    _class: JClass,
    v: jdouble,
) -> jstring {
    env.new_string(v.to_string()).unwrap().into_raw()
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_golf_tweede_JniInterface_doubleToStringRyu(
    env: JNIEnv,
    _class: JClass,
    value: jdouble,
) -> jstring {
    let mut buffer = ryu::Buffer::new();
    env.new_string(buffer.format(value)).unwrap().into_raw()
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_golf_tweede_JniInterface_doubleArrayToStringRyu(
    mut env: JNIEnv,
    _class: JClass,
    array: JDoubleArray,
) -> jstring {
    let mut buffer = ryu::Buffer::new();
    let len: usize = env.get_array_length(&array).unwrap().try_into().unwrap();
    let mut output = String::with_capacity(10 * len);

    {
        let elements = unsafe {
            env.get_array_elements_critical(&array, ReleaseMode::NoCopyBack)
                .unwrap()
        };

        for v in elements.iter() {
            output.push_str(buffer.format(*v)); // add number to output string
            output.push(' ');
        }
    }

    env.new_string(output).unwrap().into_raw()
}
