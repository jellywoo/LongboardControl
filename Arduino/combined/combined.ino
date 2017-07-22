#include <Arduino.h>
#include <SoftwareSerial.h>
#include <PWMServo.h>

SoftwareSerial bt(2, 3);

int red, green, blue;
int pwr = 4;
int redLED = 5;
int greenLED = 10;
int blueLED = 6;

PWMServo motor1;
unsigned long startTime;
float vPow = 5;
float r1 = 47000; // resistor 1
float r2 = 10000; // resistor 2
int voltcheck = 0;
int speed = 0;
int result;

void setup() {
	motor1.attach(9);
	bt.begin(9600);

	pinMode(greenLED, OUTPUT);
	pinMode(redLED, OUTPUT);
	pinMode(blueLED, OUTPUT);   
	pinMode(pwr, OUTPUT);

	digitalWrite(pwr, HIGH);

	analogWrite (redLED, 255);
	analogWrite (greenLED, 255);
	analogWrite (blueLED, 255);
}

void loop() {
	if (bt.available()) {
		char key = bt.read();

		if (key == 'a') {
			speed = bt.parseInt();
			
			if (bt.read() == '\n' && speed >= 0 && speed <= 180) {
				motor1.write(speed);
				startTime = millis();
			}
		}
	
		if (key == 'b') {
			// look for the next valid integer in the incoming serial stream:
			red = bt.parseInt();
			green = bt.parseInt();
			blue = bt.parseInt();

			if (bt.read() == '\n') {
				// constrain the values to 0 - 255
				red = constrain(red, 0, 255);
				green = constrain(green, 0, 255);
				blue = constrain(blue, 0, 255);

				// inverse
				red = 255 - red;
				green = 255 - green;
				blue = 255 - blue;

				// fill strip
				analogWrite (redLED, red);
				analogWrite (greenLED, green);
				analogWrite (blueLED, blue);
			}
		}
	}

	if (voltcheck > 30000) {
		getv();
		voltcheck = 0;
	}
	else
		voltcheck++;

	// if no response, put motor to neutral
	if ((millis() - startTime) > 300) {
		motor1.write(90); 
		delay(30); // wait for 30ms
	}
}

// get battery voltage
static void getv() {
 	// calculate voltage
	float v = (((analogRead(5) * vPow) / 1023.0) / (r2 / (r1 + r2)));
	
	if (v > 24.51)
		result = 100;
	else if (v > 23.994)
		result = 90;
	else if (v > 23.616)
		result = 80;
	else if (v > 23.298)
		result = 70;
	else if (v > 23.034)
		result = 60;
	else if (v > 22.872)
		result = 50;
	else if (v > 22.746)
		result = 40;
	else if (v > 22.482)
		result = 30;
	else if (v > 22.098)
		result = 20;
	else if (v > 19.254)
		result = 10;
	else 
		result = 0;

	bt.println(result);
	bt.flush();
}
