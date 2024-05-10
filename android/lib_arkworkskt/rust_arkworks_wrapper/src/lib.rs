extern crate jni;
extern crate ark_bls12_381;
extern crate ark_crypto_primitives;
extern crate ark_ff;
extern crate ark_groth16;
extern crate ark_mnt4_298;
extern crate ark_mnt6_298;
extern crate ark_relations;
extern crate ark_std;
extern crate ark_ec;

use jni::objects::JClass;
use jni::sys::{jint, jlong};
use jni::JNIEnv;

use ark_bls12_381::{Bls12_381, Fr as BlsFr};
use common_groth16::*;

pub mod common_groth16;


#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_uk_cam_energy_arkworkskt_ArkworksKt_benchSetup(
    _env: JNIEnv,
    _class: JClass,
    num_constraints: jint,
    num_variables: jint,
) -> jlong {
    // Run the setup
    let post_setup = groth16_setup_bls(num_constraints as u32, num_variables as u32);
    let post_setup_box = Box::new(post_setup);

    // prevents deallocation (the Java client is now responsible)
    return Box::into_raw(post_setup_box) as jlong;
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_uk_cam_energy_arkworkskt_ArkworksKt_benchProve(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) -> jlong {
    unsafe {
        let post_setup_box = Box::from_raw(ptr as *mut PostSetup<BlsFr>);

        // Run the prover
        let post_proof_box = Box::new(groth16_proof_bls(*post_setup_box.clone()));

        // disconnect from Rust to avoid deallocation after scope
        let _ = Box::into_raw(post_setup_box);

        return Box::into_raw(post_proof_box) as jlong;
    }
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_uk_cam_energy_arkworkskt_ArkworksKt_benchVerify(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) -> jint {
    unsafe {
        let post_proof_box = Box::from_raw(ptr as *mut PostProof<BlsFr, Bls12_381>);

        // Run the verifier
        let verified = groth16_verify_bls(*post_proof_box.clone());

        // disconnect from Rust to avoid deallocation after scope
        let _ = Box::into_raw(post_proof_box);

        return verified as jint;
    }
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_uk_cam_energy_arkworkskt_ArkworksKt_benchFreePostSetup(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) {
    unsafe {
        // causes deallocation after this scope ends
        drop(Box::from_raw(ptr as *mut PostSetup<BlsFr>));
    }
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_uk_cam_energy_arkworkskt_ArkworksKt_benchFreePostProof(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) {
    unsafe {
        // causes deallocation after this scope ends
        drop(Box::from_raw(ptr as *mut PostProof<BlsFr, Bls12_381>));
    }
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_uk_cam_energy_arkworkskt_ArkworksKt_verifyJniBinding(
    _env: JNIEnv,
    _class: JClass,
    input: jint,
) -> jint {
    return input + 1;
}
