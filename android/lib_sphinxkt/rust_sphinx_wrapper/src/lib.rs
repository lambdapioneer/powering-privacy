extern crate jni;
extern crate sphinx;

use sphinx::constants::{DESTINATION_ADDRESS_LENGTH, IDENTIFIER_LENGTH, NODE_ADDRESS_LENGTH};
use sphinx::crypto::keygen;
use sphinx::header::delays;
use sphinx::route::{Destination, DestinationAddressBytes, Node, NodeAddressBytes};
use sphinx::SphinxPacket;
use std::time::Duration;

use jni::objects::JClass;
use jni::sys::{jint, jlong};
use jni::JNIEnv;

struct BenchmarkSetup {
    node1: Node,
    node2: Node,
    node3: Node,
    destination: Destination,
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_uk_cam_energy_sphinxkt_SphinxKt_benchSetup(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    let (_node1_priv, node1_pub) = keygen();
    let (_node2_priv, node2_pub) = keygen();
    let (_node3_priv, node3_pub) = keygen();

    let benchmarkSetupBox = Box::new(BenchmarkSetup {
        node1: Node::new(
            NodeAddressBytes::from_bytes([1u8; NODE_ADDRESS_LENGTH]),
            node1_pub,
        ),
        node2: Node::new(
            NodeAddressBytes::from_bytes([2u8; NODE_ADDRESS_LENGTH]),
            node2_pub,
        ),
        node3: Node::new(
            NodeAddressBytes::from_bytes([3u8; NODE_ADDRESS_LENGTH]),
            node3_pub,
        ),
        destination: Destination::new(
            DestinationAddressBytes::from_bytes([4u8; DESTINATION_ADDRESS_LENGTH]),
            [4u8; IDENTIFIER_LENGTH],
        ),
    });

    // prevents deallocation (the Java client is now responsible)
    return Box::into_raw(benchmarkSetupBox) as jlong;
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_uk_cam_energy_sphinxkt_SphinxKt_benchRun(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
    iter: jint,
) {
    unsafe {
        let setup = Box::from_raw(ptr as *mut BenchmarkSetup);

        for _ in 0..iter {
            let message = vec![13u8, 16];
            let delays = delays::generate_from_average_duration(3, Duration::from_millis(10));

            SphinxPacket::new(
                message,
                &[
                    setup.node1.clone(),
                    setup.node2.clone(),
                    setup.node3.clone(),
                ],
                &setup.destination,
                &delays,
            )
            .unwrap();
        }

        // disconnect from Rust to avoid deallocation after scope
        Box::into_raw(setup);
    }
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_uk_cam_energy_sphinxkt_SphinxKt_benchFree(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) {
    unsafe {
        // causes deallocation after this scope ends
        drop(Box::from_raw(ptr as *mut BenchmarkSetup));
    }
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_uk_cam_energy_sphinxkt_SphinxKt_verifyJniBinding(
    _env: JNIEnv,
    _class: JClass,
    input: jint,
) -> jint {
    return input + 1;
}
