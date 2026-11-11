//! JNI entry points for `com.anibiar.app.nativecore.Native`. Each call is panic-safe: on any failure it returns null
//! and the Kotlin side falls back to its own implementation.

use crate::{captions, fuzzy, hls, streams, text};
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

/// `rows` is one `soft,english,tracks,hls,height` line per stream. Returns indices, best first.
#[no_mangle]
pub extern "system" fn Java_com_anibiar_app_nativecore_Native_rankStreams<'l>(mut env: JNIEnv<'l>, _c: JClass<'l>, sub_audio: jint, rows: JString<'l>) -> jintArray {
    let Some(text) = read(&mut env, &rows) else { return null_mut() };
    let Ok(ranked) = catch_unwind(|| streams::rank(sub_audio != 0, &streams::parse(&text))) else { return null_mut() };
    let Ok(arr) = env.new_int_array(ranked.len() as i32) else { return null_mut() };
    if env.set_int_array_region(&arr, 0, &ranked).is_err() {
        return null_mut();
    }
    arr.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_anibiar_app_nativecore_Native_hlsMaxHeight<'l>(mut env: JNIEnv<'l>, _c: JClass<'l>, text: JString<'l>) -> jint {
    let Some(t) = read(&mut env, &text) else { return -1 };
    catch_unwind(|| hls::max_height(&t)).unwrap_or(-1)
}

/// `items` joined with '\n'. Typo-tolerant, every query word must match. Indices best first.
#[no_mangle]
pub extern "system" fn Java_com_anibiar_app_nativecore_Native_typoRank<'l>(mut env: JNIEnv<'l>, _c: JClass<'l>, query: JString<'l>, items: JString<'l>) -> jintArray {
    let (Some(q), Some(all)) = (read(&mut env, &query), read(&mut env, &items)) else { return null_mut() };
    let Ok(ranked) = catch_unwind(AssertUnwindSafe(|| {
        let list: Vec<&str> = all.split('\n').collect();
        crate::typo::rank(&q, &list)
    })) else { return null_mut() };
    let Ok(arr) = env.new_int_array(ranked.len() as i32) else { return null_mut() };
    if env.set_int_array_region(&arr, 0, &ranked).is_err() {
        return null_mut();
    }
    arr.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_anibiar_app_nativecore_Native_recoRank<'l>(mut env: JNIEnv<'l>, _c: JClass<'l>, taste: JString<'l>, cands: JString<'l>) -> jintArray {
    let (Some(t), Some(c)) = (read(&mut env, &taste), read(&mut env, &cands)) else { return null_mut() };
    let Ok(ranked) = catch_unwind(AssertUnwindSafe(|| crate::reco::rank(&crate::reco::parse_taste(&t), &crate::reco::parse_cands(&c)))) else { return null_mut() };
    let Ok(arr) = env.new_int_array(ranked.len() as i32) else { return null_mut() };
    if env.set_int_array_region(&arr, 0, &ranked).is_err() {
        return null_mut();
    }
    arr.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_anibiar_app_nativecore_Native_statsSummary<'l>(mut env: JNIEnv<'l>, _c: JClass<'l>, today: jni::sys::jlong, rows: JString<'l>) -> jstring {
    let Some(r) = read(&mut env, &rows) else { return null_mut() };
    let Ok(out) = catch_unwind(|| crate::stats::summarize(today, &r)) else { return null_mut() };
    env.new_string(out).map(|j| j.into_raw()).unwrap_or(null_mut())
}

/// `samples` joined with ','. Returns the estimate in bits per second, or -1 when there isn't enough data.
#[no_mangle]
pub extern "system" fn Java_com_anibiar_app_nativecore_Native_netEstimate<'l>(mut env: JNIEnv<'l>, _c: JClass<'l>, samples: JString<'l>) -> jni::sys::jdouble {
    let Some(s) = read(&mut env, &samples) else { return -1.0 };
    catch_unwind(|| {
        let v: Vec<f64> = s.split(',').filter_map(|x| x.trim().parse().ok()).collect();
        crate::net::estimate(&v).unwrap_or(-1.0)
    })
    .unwrap_or(-1.0)
}

/// `samples` joined with ','. Returns `n` digit characters (0-9) describing loudness over time.
#[no_mangle]
pub extern "system" fn Java_com_anibiar_app_nativecore_Native_waveBars<'l>(mut env: JNIEnv<'l>, _c: JClass<'l>, samples: JString<'l>, n: jni::sys::jint) -> jstring {
    let Some(s) = read(&mut env, &samples) else { return null_mut() };
    let Ok(out) = catch_unwind(|| {
        let v: Vec<i32> = s.split(',').filter_map(|x| x.trim().parse().ok()).collect();
        crate::wave::bars(&v, n.max(0) as usize)
    }) else { return null_mut() };
    env.new_string(out).map(|j| j.into_raw()).unwrap_or(null_mut())
}

/// Breaks a subtitle into balanced lines that each fit `cap` characters.
#[no_mangle]
pub extern "system" fn Java_com_anibiar_app_nativecore_Native_balanceLines<'l>(mut env: JNIEnv<'l>, _c: JClass<'l>, text: JString<'l>, cap: jni::sys::jint) -> jstring {
    let Some(s) = read(&mut env, &text) else { return null_mut() };
    let Ok(out) = catch_unwind(|| crate::linebreak::balance(&s, cap.max(0) as usize)) else { return null_mut() };
    env.new_string(out).map(|j| j.into_raw()).unwrap_or(null_mut())
}

/// Returns "sample,out_w,out_h,quality,passthrough(0|1)" for sending a photo.
#[no_mangle]
pub extern "system" fn Java_com_anibiar_app_nativecore_Native_planImageNative<'l>(env: JNIEnv<'l>, _c: JClass<'l>, w: jni::sys::jint, h: jni::sys::jint, bytes: jni::sys::jlong, metered: jni::sys::jboolean) -> jstring {
    let mut env = env;
    let Ok(out) = catch_unwind(|| {
        let (s, ow, oh, q, pass) = crate::media::plan(w.max(0) as u32, h.max(0) as u32, bytes.max(0) as u64, metered != 0);
        format!("{s},{ow},{oh},{q},{}", if pass { 1 } else { 0 })
    }) else { return null_mut() };
    env.new_string(out).map(|j| j.into_raw()).unwrap_or(null_mut())
}

/// Returns `xp\tlevel\tfloor\tnext\tbadges`.
#[no_mangle]
pub extern "system" fn Java_com_anibiar_app_nativecore_Native_engageSummary<'l>(env: JNIEnv<'l>, _c: JClass<'l>, total: jni::sys::jlong, best: jint, shows: jint, max_day: jni::sys::jlong) -> jstring {
    let Ok(out) = catch_unwind(|| crate::engage::summary(total, best as i64, shows as i64, max_day)) else { return null_mut() };
    env.new_string(out).map(|j| j.into_raw()).unwrap_or(null_mut())
}
