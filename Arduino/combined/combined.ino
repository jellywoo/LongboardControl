#include <SoftwareSerial.h>
#include <PWMServo.h>
#include <Arduino.h>

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

void setup() {
	motor1.attach(9);
	bt.begin(9600);

	pinMode(greenLED, OUTPUT);
	pinMode(redLED, OUTPUT);
	pinMode(blueLED, OUTPUT);   
	pinMode(pwr, OUTPUT);

	digitalWrite(pwr, HIGH);
}

void loop() {
	if (bt.available()) {
	  	if (bt.read() == 'a') {
	  		int speed = bt.parseInt();
	  		
	  		if (bt.read() == '\n' && speed >= 0 && speed <= 180) {
	  			// bt.println("motor speed: "+String(speed));
	  			motor1.write(speed);
	  			startTime = millis();
          bt.flush();
	  		}
		}
  	
	  	// if (bt.read() == 'b') {
		  //   // look for the next valid integer in the incoming serial stream:
		  //   int red = bt.parseInt();
		  //   int green = bt.parseInt();
		  //   int blue = bt.parseInt();

		  //   if (bt.read() == '\n') {
		  //     // constrain the values to 0 - 255
		  //     red = constrain(red, 0, 255);
		  //     green = constrain(green, 0, 255);
		  //     blue = constrain(blue, 0, 255);

		  //     // bt.println("change color: "+String(red)+","+String(green)+","+String(blue));

		  //     // inverse
		  //     red = 255 - red;
		  //     green = 255 - green;
		  //     blue = 255 - blue;

		  //     // fill strip
		  //     analogWrite (redLED, red);
		  //     analogWrite (greenLED, green);
		  //     analogWrite (blueLED, blue);
		  //  }
	   // 	}

	 //   	if (bt.read() == 'c') {
	 //   		if (voltcheck > 10) {
		//         getv();
		//         voltcheck = 0;
	 //      	}

	 //      	else {
	 //        	voltcheck++;
	 //      	}
		// }
	}

	// if no response in 210ms, put motor to neutral
	if ((millis() - startTime) > 210) {
		motor1.write(90); 
		delay(30); // wait for 30ms
	}
}

// get battery voltage
static void getv() {
	float v1 = (analogRead(5) * vPow) / 1023.0;
	float v2 = v1 / (r2 / (r1 + r2));
	bt.println(String(v2) + "v");
}
