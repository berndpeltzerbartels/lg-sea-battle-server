package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WreckPhysicsTest {
    private ShipSnapshot ship(String type,double x,double y,double z,double speed) {
        return new ShipSnapshot("test","red",x,z,0,speed,0,0,2,"active","player",12,type,y,0,0,0,0,"submerged",1);
    }
    @Test void wreckSinksAtTheImpactPositionRegardlessOfItsFormerSpeed() {
        var w=new ShipWreckSnapshot("wreck",0,ship("torpedo-boat",0,0,0,17.5));
        var p=WreckPhysics.pose(w,5);
        assertEquals(0,p.z(),.00001);
        assertEquals(0,p.x(),.00001);
        assertEquals(-39.65,WreckPhysics.pose(w,19).y(),.00001);
    }
    @Test void fallingHullHitsDeepSubmarineLaterNotAtTheMomentOfDestruction() {
        var w=new ShipWreckSnapshot("wreck",0,ship("torpedo-boat",0,0,0,0));
        var sub=ship("submarine",0,-19.26,0,0);
        assertFalse(WreckPhysics.hits(w,0,sub));
        assertTrue(WreckPhysics.hits(w,9,sub));
        assertFalse(WreckPhysics.hits(w,19,sub));
    }
    @Test void successfulLateralEscapeAndRealVerticalClearanceAreSafe() {
        var w=new ShipWreckSnapshot("wreck",0,ship("torpedo-boat",0,0,0,0));
        for(double age=0;age<19;age+=.05){
            assertFalse(WreckPhysics.hits(w,age,ship("submarine",15,-19.26,0,0)));
            assertFalse(WreckPhysics.hits(w,age,ship("submarine",0,-52,0,0)));
        }
    }
    @Test void wreckDoesNotDriftIntoASubmarineOutsideItsOriginalFootprint() {
        var w=new ShipWreckSnapshot("wreck",0,ship("torpedo-boat",0,0,0,17.5));
        var sub=ship("submarine",0,-6,42,0);
        assertFalse(WreckPhysics.hits(w,0,sub));assertFalse(WreckPhysics.hits(w,6,sub));
    }
}
