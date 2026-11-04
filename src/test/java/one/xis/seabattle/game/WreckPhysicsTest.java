package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WreckPhysicsTest {
    private Vehicle ship(String type,double x,double y,double z,double speed) {
        Vehicle vehicle = Vehicle.create(type, "test", "red", new Vector2(x, z), 0, "player");
        vehicle.y = y; vehicle.speed = speed; return vehicle;
    }
    @Test void wreckSinksAtTheImpactPositionRegardlessOfItsFormerSpeed() {
        var w=new Wreck("wreck",0,ship("torpedo-boat",0,0,0,17.5));
        var p=WreckPhysics.pose(w,5);
        assertEquals(0,p.z(),.00001);
        assertEquals(0,p.x(),.00001);
        assertEquals(-39.65,WreckPhysics.pose(w,19).y(),.00001);
    }
    @Test void fallingHullHitsDeepSubmarineLaterNotAtTheMomentOfDestruction() {
        var w=new Wreck("wreck",0,ship("torpedo-boat",0,0,0,0));
        var sub=ship("submarine",0,-19.26,0,0);
        assertFalse(WreckPhysics.hits(w,0,sub));
        assertTrue(WreckPhysics.hits(w,9,sub));
        assertFalse(WreckPhysics.hits(w,19,sub));
    }
    @Test void successfulLateralEscapeAndRealVerticalClearanceAreSafe() {
        var w=new Wreck("wreck",0,ship("torpedo-boat",0,0,0,0));
        for(double age=0;age<19;age+=.05){
            assertFalse(WreckPhysics.hits(w,age,ship("submarine",15,-19.26,0,0)));
            assertFalse(WreckPhysics.hits(w,age,ship("submarine",0,-52,0,0)));
        }
    }
    @Test void wreckDoesNotDriftIntoASubmarineOutsideItsOriginalFootprint() {
        var w=new Wreck("wreck",0,ship("torpedo-boat",0,0,0,17.5));
        var sub=ship("submarine",0,-6,42,0);
        assertFalse(WreckPhysics.hits(w,0,sub));assertFalse(WreckPhysics.hits(w,6,sub));
    }
}
