use crate::data_point::SerialDataPoint;
use serialport::SerialPort;
use std::io;
use std::io::{BufRead, BufReader, Read, Write};
use std::time::Duration;
use tokio::sync::mpsc::Sender;
use tokio::time::Instant;

pub struct SerialPortReader {
    serial_port: Box<dyn SerialPort>,
}

impl SerialPortReader {
    pub async fn run(self, out: Sender<SerialDataPoint>) {
        let mut reader = BufReader::new(self.serial_port);

        // Synchronize with the beginning (three consecutive 0xFF bytes)
        let mut consecutive_ff = 0;
        while consecutive_ff < 3 {
            let mut buf = [0u8; 1];
            reader
                .read_exact(&mut buf)
                .expect("Failed to read bytes for sync.");

            if buf[0] == 0xffu8 {
                consecutive_ff += 1;
            } else {
                consecutive_ff = 0
            }
        }

        // Read header messages (normal ASCII)
        loop {
            let mut line = String::new();
            reader
                .read_line(&mut line)
                .expect("Failed to read header line.");

            eprint!(">> {}", line);
            if line.contains("start") {
                break;
            }
        }

        let mut input_pin = false;
        let mut seen_falling_edges = 0;

        // Any of the following data will is guaranteed to not contain any double 0xFF
        let start = Instant::now();
        loop {
            let mut buf = [0u8; 2];
            reader
                .read_exact(&mut buf)
                .expect("Failed to read stream bytes.");

            let data = ((buf[0] as u16) << 8) | buf[1] as u16;

            match data {
                // any measurement values will be in this range
                0x0000..=0xFFEFu16 => {
                    let time = (Instant::now() - start).as_secs_f32();
                    let data_point = SerialDataPoint {
                        time,
                        data,
                        input_pin,
                    };
                    out.send(data_point).await.expect("Channel sender dead.")
                }

                // special value for input_pin=0
                0x0FFF0u16 => {
                    input_pin = false;

                    eprint!("0");
                    io::stderr().flush().unwrap();

                    seen_falling_edges += 1;
                    eprintln!("({})", seen_falling_edges);
                }

                // special value for input_pin=1
                0x0FFF1u16 => {
                    input_pin = true;

                    eprint!("1");
                    io::stderr().flush().unwrap();
                }

                // everything else is out of spec
                _ => {
                    eprintln!("bad data: {:x}", data)
                }
            }
        }
    }

    pub fn new(serial_port_path: String) -> SerialPortReader {
        let serial_port = serialport::new(serial_port_path, 1_000_000)
            .timeout(Duration::from_millis(1_000))
            .open()
            .expect("Failed opening serial port.");
        SerialPortReader { serial_port }
    }
}
