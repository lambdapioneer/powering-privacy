$fn=64; 

// total measurements (inner bay)
tw = 62.3;
td = 78.3;
th = 3.9;//4.4;
t_pins_from_right = 10.1; // center pin

// outer padding
op = 20.0;

// inner padding
ip = 10;

// support height
sh = 3.0;

// TE battery holder 1981061-1
bh_w = 11.2+0.3; // extra for tolerance
bh_d = 2.9+0.2;
bh_h = 7.6;
bh_pins_h = 5.8;

// position relative to center pin
module battery_holder () {
    translate ([0, bh_d/2, bh_pins_h - bh_h]) {
        color ([1.0, 0, 0]) {
            cube([bh_w, bh_d, bh_h], center=true);
        }
    }
}

// main part
difference () {
    union () {
        cube([tw+op, td+op, th], center=true);
        translate ([0, 0, -(th/2+sh/2)]) {
            cube([tw+op, td+op, sh], center=true);
        }
    }
    cube([tw, td, th+0.1], center=true);
    cube([tw-ip, td-ip, th+10],center=true);
    
    translate ([tw/2-t_pins_from_right,td/2-0.1,0]) {
        battery_holder();
    }
}

// legs

lr = 3;
lh = 3;
translate ([0, 0, -(th/2 + sh)]) {
    translate([0, -15, 0]) { cylinder(lh, lr, lr); }
    translate([0, -5, 0]) { cylinder(lh, lr, lr); }
    translate([0, 5, 0]) { cylinder(lh, lr, lr); }
    translate([0, 15, 0]) { cylinder(lh, lr, lr); }
}
