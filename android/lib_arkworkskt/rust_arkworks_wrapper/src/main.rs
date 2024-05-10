extern crate jni;
extern crate ark_bls12_381;
extern crate ark_crypto_primitives;
extern crate ark_ec;
extern crate ark_ff;
extern crate ark_groth16;
extern crate ark_mnt4_298;
extern crate ark_mnt6_298;
extern crate ark_relations;
extern crate ark_std;

use common_groth16::*;

mod common_groth16;

// Used for debugging only
fn main() {
    for size in vec![100, 1000, 10_000, 100_000] {
        let post_setup = groth16_setup_bls(size, size);
        let post_proof = groth16_proof_bls(post_setup.clone());
        let verified = groth16_verify_bls(post_proof.clone());
        println!("{size} -> {verified}")
    }
}
