package one.xis.seabattle.game;

/** Deterministic hull motion, shared as parameters with the renderer. No rigid-body engine. */
final class WreckPhysics {
    static final double DURATION = 19;
    static final double SINK_DEPTH = 32 + 2.35 * SeaBattleGameConfig.TORPEDO_BOAT_SCALE;
    private static final double SCALE = SeaBattleGameConfig.TORPEDO_BOAT_SCALE;
    private static final double[][] WIDTHS = {{-4.2,.31},{-3.45,.53},{-2.25,.73},{-1.1,.79},
            {-.22,.79},{-.04,.78},{1.25,.67},{2.42,.436},{2.72,.35},{3.18,.21},{3.68,.01}};

    record Pose(double x, double y, double z, double pitch, double yaw, double roll) {}
    static Pose pose(ShipWreckSnapshot wreck, double age) {
        ShipSnapshot s = wreck.ship();
        age = Math.max(0, age);
        double t = MathSupport.clamp(age / wreck.sinkSeconds(), 0, 1);
        double ease = t < .5 ? 4*t*t*t : 1 - Math.pow(-2*t+2, 3)/2;
        boolean submerged = "submarine".equals(s.vehicleType()) && s.y() < -.88;
        return new Pose(s.x(), ("torpedo-boat".equals(s.vehicleType()) ? -.2*SCALE : s.y())-ease*wreck.sinkDepth(),
                s.z(), submerged ? 0 : -.28*ease, s.heading(),
                submerged ? 0 : wreck.sinkSide()*(.12+1.45*ease));
    }

    static boolean hits(ShipWreckSnapshot wreck, double age, ShipSnapshot target) {
        if (age < 0 || age >= wreck.sinkSeconds() || "scout-plane".equals(wreck.ship().vehicleType())
                || "scout-plane".equals(target.vehicleType())) return false;
        Pose p = pose(wreck, age);
        if (Math.hypot(p.x-target.x(),p.z-target.z()) > 38 || Math.abs(p.y-target.y()) > 19) return false;
        double sy=Math.sin(p.yaw),cy=Math.cos(p.yaw),sp=Math.sin(p.pitch),cp=Math.cos(p.pitch),sr=Math.sin(p.roll),cr=Math.cos(p.roll);
        // Columns of Babylon's RotationYawPitchRoll matrix: right, up, forward.
        double[][] axes={{cy*cr+sy*sp*sr,cp*sr,-sy*cr+cy*sp*sr},
                {-cy*sr+sy*sp*cr,cp*cr,sy*sr+cy*sp*cr},{sy*cp,-sp,cy*cp}};
        boolean sub="submarine".equals(target.vehicleType()), wreckSub="submarine".equals(wreck.ship().vehicleType());
        double min=sub?-5.02:-4.2,max=sub?5.02:3.68;
        double targetY=(sub?target.y():-.2*SCALE)+(sub?.105:.345)*SCALE,halfHeight=(sub?.405:.395)*SCALE;
        double sin=Math.sin(target.heading()),cos=Math.cos(target.heading());
        int steps=(int)Math.ceil((max-min)*SCALE/.6);
        // Sample the actual long hull, not just its centre; inflate by its cross-section.
        for(int i=0;i<=steps;i++) {
            double z=min+(max-min)*i/steps;
            double width=(sub?submarineWidth(z):hullWidth(z))*SCALE;
            double dx=target.x()+sin*z*SCALE-p.x,dy=targetY-p.y,dz=target.z()+cos*z*SCALE-p.z;
            double[] local=new double[3],margin=new double[3];
            for(int a=0;a<3;a++) {
                local[a]=(dx*axes[a][0]+dy*axes[a][1]+dz*axes[a][2])/SCALE;
                margin[a]=(Math.abs(axes[a][0]*cos-axes[a][2]*sin)*width+Math.abs(axes[a][1])*halfHeight)/SCALE+.025;
            }
            double lo=wreckSub?-5.02:-4.2,hi=wreckSub?5.02:3.68;
            if(local[2]<lo-margin[2]||local[2]>hi+margin[2])continue;
            double f=MathSupport.clamp(local[2],lo,hi);
            double w=wreckSub?submarineWidth(f):hullWidth(f);
            if(Math.abs(local[0])<=w+margin[0] && local[1]>=(wreckSub?-.3:-.05)-margin[1]
                    && local[1]<=(wreckSub?.51:.74)+margin[1])return true;
        }
        return false;
    }
    private static double hullWidth(double z) {
        for(int i=1;i<WIDTHS.length;i++)if(z<=WIDTHS[i][0]) {
            double[] a=WIDTHS[i-1],b=WIDTHS[i];return a[1]+(b[1]-a[1])*MathSupport.clamp((z-a[0])/(b[0]-a[0]),0,1);
        }
        return .01;
    }
    private static double submarineWidth(double z) {
        return .62*Math.sqrt(Math.max(.001,1-Math.pow(z/5.04,2)));
    }
}
