#include <Adafruit_INA219.h>
#include <Wire.h>

Adafruit_INA219 *ina219;

#define MPS_LIMIT 2500
#define MICROS_PER_OP (1000000 / MPS_LIMIT)

#define LED_PIN 13
#define INPUT_PIN 14

unsigned long last_sent_finished;
int last_input;

// Protocol overview
// -----------------
// Data is sent in blocks of two bytes. Based on their value
// they are interpreted as follows:
//
// 0x0000..0xFFEF  the raw power measurements in low-endian uint16_t
// 0xFFF0          the input port changed to low
// 0xFFF1          the input port changed to high
// 0xFFF2..0xFFF3  [reserved]
// 0xFFFF          synchronisation before intro sequence

void setup(void) {
    // start serial
    Serial.begin(1000000);
    while (!Serial) {
        delay(1);
    }
    Serial.write("\xff\xff\xff");
    Serial.println(
        "# Compiled on '"__DATE__
        "' at '"__TIME__
        "'");

    // setup other I/O
    pinMode(LED_PIN, OUTPUT);
    pinMode(INPUT_PIN, INPUT);
    last_input = 0;

    // start I2C devices
    ina219 = new Adafruit_INA219();
    if (!ina219->begin(&Wire)) {
        Serial.println("# Failed to find INA219 chip");
        while (1) {
            delay(10);
        }
    }

    ina219->setCalibration_16V_400mA();
    // see documentation: ina219->setCalibration_CUSTOM();

    // print debug information in header lines
    debug_mps();
    Serial.print("# input pin: ");
    Serial.println(INPUT_PIN);

    // signals begin of the main protocol; no ASCII beyond this point
    Serial.println("start");

    last_sent_finished = micros();
}

void debug_mps(void) {
    unsigned long start_t = millis();
    unsigned long cnt = 0;
    while (millis() < start_t + 1000) {
        cnt++;
        ina219->getPower_raw();
    }
    Serial.print("# mps:");
    Serial.print(cnt);
    Serial.print(" (limit:");
    Serial.print(MPS_LIMIT);
    Serial.print(")");
    Serial.println();
}

void loop(void) {
    // Read pin (and report change)
    const auto curr_input = digitalRead(INPUT_PIN);
    if (curr_input != last_input) {
        digitalWrite(LED_PIN, curr_input);
        last_input = curr_input;
        Serial.write((uint8_t)(0xFF));
        if (curr_input == LOW) {
            Serial.write((uint8_t)(0xF0));
        } else if (curr_input == HIGH) {
            Serial.write((uint8_t)(0xF1));
        }
    }

    // Read power
    uint16_t power_raw = ina219->getPower_raw();
    if (power_raw >= 0xFFF0) {
        // with leading 0xFFF is special
        power_raw = 0xFFEF;
    }

    Serial.write((uint8_t)(power_raw >> 8));
    Serial.write((uint8_t)(power_raw));

    // Pause to match our target rate
    unsigned long deadline = last_sent_finished + MICROS_PER_OP;
    while (true) {
        unsigned long now = micros();
        // micros() will overflow every ~70 minutes
        if (now <= last_sent_finished) {
            last_sent_finished = micros();
            break;
        }

        // wait until deadline passed
        if (now >= deadline) {
            last_sent_finished = deadline;
            break;
        };
    }
}
