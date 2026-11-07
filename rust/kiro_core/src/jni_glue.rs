//! JNI entry points for `com.anibiar.app.nativecore.Native`. Each call is panic-safe: on any failure it returns null
//! and the Kotlin side falls back to its own implementation.

use crate::{captions, fuzzy, text};
use jni::objects::{JClass, JString};
use jni::sys::{jdouble, jint, jintArray, jstring};
use jni::JNIEnv;
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::ptr::null_mut;

fn read(env: &mut JNIEnv, s: &JString) -> Option<String> {
    env.get_string(s).ok().map(String::from)
}

#[no_mangle]
pub extern "system" fn Java_com_anibiar_app_nativecore_Native_parseCaptions<'l>(mut env: JNIEnv<'l>, _c: JClass<'l>, raw: JString<'l>) -> jstring {
    let Some(text) = read(&mut env, &raw) else { return null_mut() };
    let Ok(out) = catch_unwind(|| captions::encode(&captions::parse(&text))) else { return null_mut() };
    env.new_string(out).map(|s| s.into_raw()).unwrap_or(null_mut())
}

/// `items` is the candidate strings joined with '\n'. Returns matching indices, best first.
#[no_mangle]
pub extern "system" fn Java_com_anibiar_app_nativecore_Native_fuzzyRank<'l>(mut env: JNIEnv<'l>, _c: JClass<'l>, query: JString<'l>, items: JString<'l>) -> jintArray {
    let (Some(q), Some(all)) = (read(&mut env, &query), read(&mut env, &items)) else { return null_mut() };
    let Ok(ranked) = catch_unwind(AssertUnwindSafe(|| {
        let list: Vec<&str> = all.split('\n').collect();
        fuzzy::rank(&q, &list)
    })) else { return null_mut() };
    let Ok(arr) = env.new_int_array(ranked.len() as i32) else { return null_mut() };
    if env.set_int_array_region(&arr, 0, &ranked).is_err() {
        return null_mut();
    }
    arr.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_anibiar_app_nativecore_Native_version<'l>(env: JNIEnv<'l>, _c: JClass<'l>) -> jstring {
    env.new_string(concat!("kiro_core ", env!("CARGO_PKG_VERSION"))).map(|s| s.into_raw()).unwrap_or(null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_anibiar_app_nativecore_Native_dice<'l>(mut env: JNIEnv<'l>, _c: JClass<'l>, a: JString<'l>, b: JString<'l>) -> jdouble {
    let (Some(x), Some(y)) = (read(&mut env, &a), read(&mut env, &b)) else { return -1.0 };
    catch_unwind(|| text::dice(&x, &y)).unwrap_or(-1.0)
}

#[no_mangle]
pub extern "system" fn Java_com_anibiar_app_nativecore_Native_htmlText<'l>(mut env: JNIEnv<'l>, _c: JClass<'l>, s: JString<'l>, mode: jint) -> jstring {
    let Some(input) = read(&mut env, &s) else { return null_mut() };
    let Ok(out) = catch_unwind(|| text::html_text(&input, mode)) else { return null_mut() };
    env.new_string(out).map(|j| j.into_raw()).unwrap_or(null_mut())
}
