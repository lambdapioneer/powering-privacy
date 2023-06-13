#[derive(Debug)]
pub struct SerialDataPoint {
    pub time: f32,
    pub data: u16,
    pub input_pin: bool,
}
