use ark_bls12_381::{Bls12_381, Fr as BlsFr};
use ark_crypto_primitives::snark::SNARK;
use ark_ec::pairing::Pairing;
use ark_ff::{PrimeField, UniformRand};
use ark_groth16::{Groth16, Proof, ProvingKey, VerifyingKey};
use ark_relations::{
    lc,
    r1cs::{ConstraintSynthesizer, ConstraintSystemRef, SynthesisError},
};
use ark_std::rand::SeedableRng;

#[derive(Copy)]
struct DummyCircuit<F: PrimeField> {
    pub a: Option<F>,
    pub b: Option<F>,
    pub num_variables: usize,
    pub num_constraints: usize,
}

impl<F: PrimeField> Clone for DummyCircuit<F> {
    fn clone(&self) -> Self {
        DummyCircuit {
            a: self.a.clone(),
            b: self.b.clone(),
            num_variables: self.num_variables.clone(),
            num_constraints: self.num_constraints.clone(),
        }
    }
}

// From https://github.com/arkworks-rs/groth16/blob/master/benches/bench.rs#L42
impl<F: PrimeField> ConstraintSynthesizer<F> for DummyCircuit<F> {
    fn generate_constraints(self, cs: ConstraintSystemRef<F>) -> Result<(), SynthesisError> {
        let a = cs.new_witness_variable(|| self.a.ok_or(SynthesisError::AssignmentMissing))?;
        let b = cs.new_witness_variable(|| self.b.ok_or(SynthesisError::AssignmentMissing))?;
        let c = cs.new_input_variable(|| {
            let a = self.a.ok_or(SynthesisError::AssignmentMissing)?;
            let b = self.b.ok_or(SynthesisError::AssignmentMissing)?;

            Ok(a * b)
        })?;

        for _ in 0..(self.num_variables - 3) {
            let _ = cs.new_witness_variable(|| self.a.ok_or(SynthesisError::AssignmentMissing))?;
        }

        for _ in 0..self.num_constraints - 1 {
            cs.enforce_constraint(lc!() + a, lc!() + b, lc!() + c)?;
        }

        cs.enforce_constraint(lc!(), lc!(), lc!())?;

        Ok(())
    }
}


#[derive(Clone)]
pub struct PostSetup<F: PrimeField> {
    circuit: DummyCircuit<F>,
    setup_data: SetupBls,
}

pub type SetupBls = (ProvingKey<Bls12_381>, VerifyingKey<Bls12_381>);

pub fn groth16_setup_bls(num_constraints: u32, num_variables: u32) -> PostSetup<BlsFr> {
    let rng = &mut ark_std::rand::rngs::StdRng::seed_from_u64(0u64);
    let c = DummyCircuit::<BlsFr> {
        a: Some(<BlsFr>::rand(rng)),
        b: Some(<BlsFr>::rand(rng)),
        num_variables: num_variables as usize,
        num_constraints: num_constraints as usize,
    };

    let pk_vk = Groth16::<Bls12_381>::circuit_specific_setup(c, rng).unwrap();

    return PostSetup {
        circuit: c,
        setup_data: pk_vk,
    };
}

#[derive(Clone)]
pub struct PostProof<F: PrimeField, M: Pairing> {
    circuit: DummyCircuit<F>,
    setup_data: SetupBls,
    proof: Proof<M>,
}

pub fn groth16_proof_bls(post_setup: PostSetup<BlsFr>) -> PostProof<BlsFr, Bls12_381> {
    let rng = &mut ark_std::rand::rngs::StdRng::seed_from_u64(0u64);

    let proof = Groth16::<Bls12_381>::prove(&post_setup.setup_data.0, post_setup.circuit.clone(), rng).unwrap();

    return PostProof {
        circuit: post_setup.circuit,
        setup_data: post_setup.setup_data,
        proof,
    };
}

pub fn groth16_verify_bls(post_proof: PostProof<BlsFr, Bls12_381>) -> bool {
    let v = post_proof.circuit.a.unwrap() * post_proof.circuit.b.unwrap();
    return Groth16::<Bls12_381>::verify(&post_proof.setup_data.1, &vec![v], &post_proof.proof).unwrap();
}
