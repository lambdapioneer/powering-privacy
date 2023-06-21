# Assembly instructions

Read the entire documentation before starting to build the hardware kit.

> **Warning**
> Working with batteries of any kind is dangerous. Only continue if you are certain that you know how to use the setup safely. In particular: if there are any deformations, heat, or smoke, disconnect everything immediately and bring the battery outside using a fire-safe container.


## Step 1: breadboard assembly (1 hour)

**Required tools:** Soldering station, wire cutter, wire stripper

**Required skills:** Soldering, working with breadboards

**Required material:** Arduino Nano Every, INA219, Breadboard, 6.8kΩ resistor, Breadboard wires

First, follow the instructions for the Arduino and INA219 components to finish their break out assembly. Typically, this involves soldering the header pins to the components. 

Then, connect the components on the breadboard as shown in the following image:

![Breadboard assembly](figures/breadboard-overview.png)

Note that the resistor is 6.8kΩ, and not as shown in the picture 220Ω. Your Arduino and INA219 breakout boards might look differently.

You can now test this setup using the instructions in the [Arduino section](../arduino/README.md). Together with the [rsoxy](../rsoxy/README.md) serial logger, you should be able to see measurements of ~0mW.


## Step 2: 3D print components (4 hours)

**Required tools:** 3D printer, slicer software

**Required skills:** 3D printing

**Required material:** 3D printing material

Use the files and instruction in the [3D parts](../3d-parts/README.md) section to print the required components. You should end up with the following parts:


![3d printed battery holder](figures/3d-print-battery-holder.jpg) ![3d printed battery replacement](figures/3d-print-battery-replacement.jpg) 




## Step 3: battery holder assembly (2 hours + 24 hours for epoxy to cure)

**Required tools:** Soldering station, wire cutter, wire stripper, crimping tool

**Required skills:** Working with two-part epoxy, soldering

**Required material:** Leaf battery connectors, two-part epoxy, gloves, wires

Insert the leaf battery connector component into the respective cut-out of the battery holder. Be careful when inserting it and push in the connectors with a screwdriver or similar tool. While there should be some resistance, if the component does not fit use a file or similar to remove excessive material from the 3D printed part. Once inserted, the component should look like this:

![Battery holder with leaf battery connector](figures/holder-1.jpg)

Flip the component and solder the wires to the leaf battery connector. Make the black (ground) and yellow (data) wires about 8cm long. Make the red (positive) wire about 15cm long so that it can be later connected to the INA219 screw terminal. Use the crimp tools to add ferrules to the wires. The component should look like this:

![Battery holder attached wires](figures/holder-2.jpg)

Now, use the two-part epoxy to glue the leaf battery connector to the battery holder. Use some additional epoxy on top of the wires to give them extra support. Also glue the small "feets" to the bottom of the battery holder. The final component should look like this:

![Battery holder complete](figures/holder-3.jpg)

Let the epoxy cure for 12 hours.

Insert a battery into the component and check that the connections align well. Use a digital multimeter to measure the voltage between the red and black wires. It should be around 3.8V. If not, check the connections and the battery. 


## Step 4: battery inlet assembly (2 hours + 24 hours for epoxy to cure)

**Required tools:** Soldering station, wire cutter, wire stripper, crimping tool, tweezers

**Required skills:** Working with two-part epoxy, soldering, working with small parts

**Required material:** SD card adapter, two-part epoxy, gloves, wires

Inspect the 3D printed battery inlet and identify the spaces for the contacts. If necessary, use a file to ensure that the corners are free of excessive material and that the surface is smooth.

Break the SD card adapter open and remove the contacts. Use the tweezers and wire cutter to remove individual contacts from it. You will need at least three. Use the 3D printed part and the tweezers to bend them so that their front is flush with the cut-outs of the 3D printed part and their end lies on the back of the component. Once all components are "harvested" and bent, apply a small amount of two-part epoxy for each contact and carefully insert them into space using a tweezer. The following pictures illustrate the process:

![Opened SD card adapter](figures/contacts-1.jpg)
![Breaking off an individual contact](figures/contacts-2.jpg)
![Four harvested contacts](figures/contacts-3.jpg)
![Contact bent into the right shape](figures/contacts-4.jpg)
![All three contacts glued into place](figures/contacts-5.jpg)

Be careful for the first 5 minutes while the epoxy hardens. Then wait another 12 hours for the epoxy to cure completely.

Now, solder the wires to the contacts. Make the black (ground) and yellow (data) wires about 10cm long. Make the red (positive) wire about 20cm long so that it can be later connected to the INA219 screw terminal. Use the crimp tools to add ferrules to the wires. The component should look like this:

![Battery inlet with wires](figures/inlet-1.jpg)

Then add some epoxy to the wires to give them extra support. The component should look like this:

![Battery inlet with wires and epoxy](figures/inlet-2.jpg)

Let the epoxy cure for 12 hours.

Once everything is cured, insert a battery into the component and check that the connections align well. Alternatively, you can insert it into the battery holder and check good connectivity between the wire pairs of the same color.


## Step 5: final assembly (1 hour)

**Required tools:** Screw drivers

**Required material:** Terminal connector block

Use the terminal connector block to directly connect the black (ground) and yellow (data) wires of the battery inlet and the battery holder.
Connect the red (positive) wires to the screw terminal of the INA219. The final assembly should look like this:

![Final assembly](figures/complete.jpg)

> **Warning**
> The displayed image misses an important ground connection between the ground cables of the battery (black wires) and the breadboard. See [this image](figures/hw-overall.jpg) for a correct reference. Missing the ground connection will result in incorrect measurements.

Insert the battery in the battery holder and turn on the phone.


## Step 6: using the USB-to-serial adapter (1 hour)

**Required material:** USB-OTG cable, USB-to-serial adapter, male to female jumper wires

This step is only required for micro studies where we need to synchronize the clocks.

Find the jumper on the side of the USB-to-serial adapter and set it to 5V. Use the jumper wires to connect GND to ground on the Arduino board, and RTS to port A0/D14 (the one with the resistor).

With the USB-to-serial adapter, the layout should look similar to this:

![overall](figures/hw-overall.jpg)