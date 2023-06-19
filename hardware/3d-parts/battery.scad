$fn=64; 

// total measurements
tw = 62.0;
td = 77.7;
th = 4.4;

// cylinders
r = th / 2;

// block
bw = tw - r - r;

// edges for the battery holding slots
ew = 2.0;
eh = (4.4-1.7)/2;
ed = 1.4+0.2;

// optional inner hole padding
ip = 20;

difference () {
    union () {
        cube([bw, td, th], true);
        translate ([bw/2,td/2,0]) {
            rotate ([90,0,0]) {
                cylinder(td, r, r);
            }
        }
        translate ([-bw/2,td/2,0]) {
            rotate ([90,0,0]) {
                cylinder(td, r, r);
            }
        }
    }
    
    // edges to slot in
    translate ([bw/2,td/2,th/2]) {
        cube([ew*2, ed*2, eh*2], center=true);
    }
    translate ([bw/2,td/2,-th/2]) {
        cube([ew*2, ed*2, eh*2], center=true);
    }
    translate ([-bw/2,td/2,th/2]) {
        cube([ew*2, ed*2, eh*2], center=true);
    }
    translate ([-bw/2,td/2,-th/2]) {
        cube([ew*2, ed*2, eh*2], center=true);
    }
    
    // empty middle (to save material and time)
    cube([tw-ip, td-ip, th+1], center=true);
    
    // small spaces for the contacts
    sw=2.0;
    sfromside=6.9;
    sinterval=2.9;
    sd=0.8;
    for ( x = [0 : sinterval : 2*sinterval] ){
        sc=tw/2-sfromside;
        translate ([sc-x,td/2,0]) {
            cube([sw, 2*sd, th+0.1], center=true);
        }
    }
}