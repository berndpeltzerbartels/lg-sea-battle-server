package one.xis.seabattle.game;

import java.util.ArrayList;
import java.util.List;

/** Model-space collision volumes, prepared once from client submarineModel.js hull sections. */
final class SubmarineProjectileGeometry {
    private static final double[][] SECTIONS = {
            {-5.02,.04,.04,.018}, {-4.55,.26,.19,.12}, {-3.55,.46,.36,.22},
            {-2,.56,.46,.26}, {-.25,.62,.51,.3}, {.75,.58,.48,.27},
            {1.75,.46,.38,.22}, {2.85,.32,.27,.15}, {3.8,.21,.18,.08},
            {4.55,.11,.09,.032}, {5.02,.028,.025,.01}
    };
    private static final List<double[][]> HULL = hullVolumes();
    private static final double[][] TOWER_SECTIONS = {
            {-1.0, .06}, {-.98, .075}, {-.955, .105}, {-.92, .15},
            {-.875, .215}, {-.82, .3}, {-.75, .38}, {-.66, .47},
            {-.56, .53}, {-.45, .575}, {-.32, .6}, {-.14, .61},
            {.05, .595}, {.18, .57}, {.29, .535}, {.39, .47},
            {.48, .405}, {.56, .335}, {.63, .265}, {.69, .205},
            {.74, .18}, {.785, .15}, {.82, .13}, {.845, .115}
    };
    private record TowerVolume(double minZ, double maxZ, double[][] planes) {}
    private static final List<TowerVolume> TOWER = towerVolumes();

    static double hitFraction(double ax, double ay, double az, double bx, double by, double bz, Ship ship) {
        double scale = SeaBattleGameConfig.TORPEDO_BOAT_SCALE;
        double cos = Math.cos(ship.heading()), sin = Math.sin(ship.heading());
        double ox = ax - ship.position().x(), oz = az - ship.position().z();
        double x = (ox*cos-oz*sin)/scale, y = (ay-ship.y())/scale, z = (ox*sin+oz*cos)/scale;
        double dx = ((bx-ax)*cos-(bz-az)*sin)/scale, dy = (by-ay)/scale;
        double dz = ((bx-ax)*sin+(bz-az)*cos)/scale;
        double nearest = Double.POSITIVE_INFINITY;
        for (double[][] planes : HULL) nearest = Math.min(nearest, clip(planes,x,y,z,dx,dy,dz));
        if (Math.max(y, y + dy) >= .6195 && Math.min(y, y + dy) <= 1.434) {
            double minZ = Math.min(z, z + dz), maxZ = Math.max(z, z + dz);
            for (TowerVolume volume : TOWER) {
                if (maxZ >= volume.minZ() && minZ <= volume.maxZ()) {
                    nearest = Math.min(nearest, clip(volume.planes(), x, y, z, dx, dy, dz));
                }
            }
        }
        double mastBase = .66 + .64*.9 - .012;
        nearest = Math.min(nearest, mast(x,y,z,dx,dy,dz,.022,mastBase,mastBase+.986*.9));
        nearest = Math.min(nearest, mast(x,y,z,dx,dy,dz,.184,mastBase,mastBase+.816*.9));
        return nearest;
    }

    private static double mast(double x,double y,double z,double dx,double dy,double dz,double mastZ,double bottom,double top) {
        double lo=0, hi=1;
        if (Math.abs(dy)<1e-12) { if (y<bottom || y>top) return Double.POSITIVE_INFINITY; }
        else {
            double a=(bottom-y)/dy, b=(top-y)/dy;
            lo=Math.max(lo,Math.min(a,b)); hi=Math.min(hi,Math.max(a,b));
        }
        double qz=z-mastZ, a=dx*dx+dz*dz, b=2*(x*dx+qz*dz), c=x*x+qz*qz-.019*.019;
        if (a<1e-20) { if(c>0) return Double.POSITIVE_INFINITY; }
        else {
            double disc=b*b-4*a*c;
            if(disc<0) return Double.POSITIVE_INFINITY;
            double root=Math.sqrt(disc);
            lo=Math.max(lo,(-b-root)/(2*a)); hi=Math.min(hi,(-b+root)/(2*a));
        }
        return lo<=hi ? lo : Double.POSITIVE_INFINITY;
    }

    private static List<TowerVolume> towerVolumes() {
        // Match the visible tower's outline, intermediate sections and raised front rim.
        List<double[]> sections = new ArrayList<>();
        for (int i = 1; i < TOWER_SECTIONS.length; i++) {
            double[] a = TOWER_SECTIONS[i - 1], b = TOWER_SECTIONS[i];
            sections.add(a);
            sections.add(new double[]{(a[0] + b[0]) / 2, (a[1] + b[1]) / 2});
        }
        sections.add(TOWER_SECTIONS[TOWER_SECTIONS.length - 1]);
        List<TowerVolume> volumes = new ArrayList<>();
        for (int i = 1; i < sections.size(); i++) {
            List<double[]> vertices = new ArrayList<>();
            for (double[] section : new double[][]{sections.get(i - 1), sections.get(i)}) {
                double ratio = Math.max(0, Math.min(1, (section[0] + .36) / .24));
                double rim = .7 + .16 * ratio * ratio * (3 - 2 * ratio);
                double z = .04 + .9 * section[0];
                for (int side : new int[]{-1, 1}) {
                    vertices.add(new double[]{side * .9 * (section[1] / 2 + .028), .66 - .9 * .045, z});
                    vertices.add(new double[]{side * .9 * section[1] / 2, .66 + .9 * rim, z});
                }
            }
            volumes.add(new TowerVolume(.04 + .9 * sections.get(i - 1)[0],
                    .04 + .9 * sections.get(i)[0], convexPlanes(vertices)));
        }
        return List.copyOf(volumes);
    }

    private static double clip(double[][] planes,double x,double y,double z,double dx,double dy,double dz) {
        double lo=0, hi=1;
        for(double[] p:planes) {
            double distance=p[0]*x+p[1]*y+p[2]*z-p[3];
            double rate=p[0]*dx+p[1]*dy+p[2]*dz;
            if(Math.abs(rate)<1e-12) { if(distance>1e-9) return Double.POSITIVE_INFINITY; }
            else {
                double t=-distance/rate;
                if(rate<0) lo=Math.max(lo,t); else hi=Math.min(hi,t);
                if(lo>hi) return Double.POSITIVE_INFINITY;
            }
        }
        return lo;
    }

    private static List<double[][]> hullVolumes() {
        List<double[][]> volumes=new ArrayList<>();
        for(int i=1;i<SECTIONS.length;i++) {
            List<double[]> vertices=new ArrayList<>();
            for(double[] section:new double[][]{SECTIONS[i-1],SECTIONS[i]}) {
                double[] ys={.62,.4,-.08,-.27}, widths={section[2],section[1],section[1]*.78,section[3]};
                for(int j=0;j<4;j++) for(int side:new int[]{-1,1}) vertices.add(new double[]{side*widths[j],ys[j],section[0]});
            }
            volumes.add(convexPlanes(vertices));
        }
        return List.copyOf(volumes);
    }

    // Only startup work: supporting planes of each pair of adjacent cross-sections.
    private static double[][] convexPlanes(List<double[]> points) {
        List<double[]> planes=new ArrayList<>();
        for(int i=0;i<points.size();i++) for(int j=i+1;j<points.size();j++) for(int k=j+1;k<points.size();k++) {
            double[] a=points.get(i), b=points.get(j), c=points.get(k);
            double ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2],vx=c[0]-a[0],vy=c[1]-a[1],vz=c[2]-a[2];
            double nx=uy*vz-uz*vy, ny=uz*vx-ux*vz, nz=ux*vy-uy*vx;
            double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
            if(length<1e-10) continue;
            nx/=length; ny/=length; nz/=length;
            double d=nx*a[0]+ny*a[1]+nz*a[2];
            boolean positive=false,negative=false;
            for(double[] p:points) {
                double side=nx*p[0]+ny*p[1]+nz*p[2]-d;
                positive|=side>1e-9; negative|=side< -1e-9;
            }
            if(positive&&negative) continue;
            if(positive) { nx=-nx;ny=-ny;nz=-nz;d=-d; }
            boolean duplicate=false;
            for(double[] p:planes) if(Math.abs(p[0]-nx)+Math.abs(p[1]-ny)+Math.abs(p[2]-nz)+Math.abs(p[3]-d)<1e-8) {duplicate=true;break;}
            if(!duplicate) planes.add(new double[]{nx,ny,nz,d});
        }
        return planes.toArray(double[][]::new);
    }
}
