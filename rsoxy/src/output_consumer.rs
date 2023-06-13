use crate::data_point::SerialDataPoint;
use std::fs::File;
use std::io::{LineWriter, Write};
use std::path::PathBuf;
use tokio::sync::mpsc::Receiver;

pub struct OutputConsumer {
    output_path: PathBuf,
}

impl OutputConsumer {
    pub async fn run(&self, mut receiver: Receiver<SerialDataPoint>) {
        let file = File::create(&self.output_path).expect("Failed to create output file.");
        let mut writer = LineWriter::new(file);

        // write CSV header
        writer
            .write_fmt(format_args!("time_s,power_mw,input_pin\n"))
            .expect("Failed to write CSV header.");

        while let Some(data_point) = receiver.recv().await {
            writer
                .write_fmt(format_args!(
                    "{},{},{}\n",
                    data_point.time,
                    data_point.data,
                    if data_point.input_pin { 1 } else { 0 }
                ))
                .expect("Failed to write data line.");
        }
    }
}

impl OutputConsumer {
    pub fn new(output_path: PathBuf) -> OutputConsumer {
        OutputConsumer { output_path }
    }
}
