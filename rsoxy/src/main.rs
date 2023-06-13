use std::path::PathBuf;

use clap::Parser;

use tokio::sync::mpsc;

use output_consumer::OutputConsumer;
use serial_port_reader::SerialPortReader;

mod data_point;
mod output_consumer;
mod serial_port_reader;

#[derive(Parser)]
#[command(author, version, about, long_about = None)]
struct Cli {
    /// The output log file
    file: PathBuf,

    /// The serial port to open and read from
    #[arg(default_value = "/dev/ttyACM0")]
    port: String,
}

fn main() {
    let cli_args = Cli::parse();

    let rt = tokio::runtime::Runtime::new().unwrap();
    rt.block_on(run(cli_args));
}

async fn run(cli_args: Cli) {
    let (messages_tx, messages_rx) = mpsc::channel(10 * 1024);

    // start serial reader
    let serial_port_reader = SerialPortReader::new(cli_args.port);
    let mut spr_handle = tokio::spawn(async move {
        serial_port_reader.run(messages_tx).await;
    });

    // start consumer
    let consumer = OutputConsumer::new(cli_args.file);
    let mut oc_handle = tokio::spawn(async move {
        consumer.run(messages_rx).await;
    });

    // wait for either to fail (but more likely, the user terminating the program)
    tokio::select! {
        _ = (&mut spr_handle) => {
            eprintln!("SerialPortReader ended. Abort.");
            oc_handle.abort();
        },
        _ = (&mut oc_handle) => {
            eprintln!("OutputConsumer ended. Abort.");
            spr_handle.abort();
        },
    }
}
