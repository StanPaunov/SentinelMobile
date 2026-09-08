package com.stanpaunov.sentinelmobile;

/** No framework dependencies: javac + java with -ea. */
public final class BatteryEstimatorTest {
    private static void check(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) {
        BatteryEstimator e=new BatteryEstimator();
        check(e.estimate(0,80,false,100000)<0,"No estimate while charging");
        check(e.estimate(0,-1,true,100000)<0,"Invalid level");
        check(e.estimate(0,80,true,-1)<0,"Initial sample");
        check(e.estimate(30000,79,true,-1)<0,"Under one minute");
        e.reset();
        e.estimate(0,90,true,-1);
        check(e.estimate(60000,89,true,-1)<0,"One rounded percentage point is insufficient");
        e.estimate(120000,88,true,-1);
        long steady=e.estimate(180000,87,true,-1);
        check(steady==87*60000L,"Steady discharge estimate");
        long later=e.estimate(185000,87,true,-1);
        check(later==steady-5000,"Countdown continues on five-second refresh");
        check(e.estimate(190000,88,true,-1)<0,"Rising battery resets samples");
        e.reset();e.estimate(0,90,true,-1);e.estimate(60000,89,true,-1);e.estimate(120000,88,true,-1);
        check(e.estimate(121000,87,true,-1)<0,"Unstable discharge rejected");
        e.reset();e.estimate(0,80,true,-1);
        check(e.estimate(360000,77,true,-1)<0,"Background gap rejected");
        e.reset();
        check(e.estimate(0,80,true,3600000)==3600000,"System prediction preferred");
        check(e.estimate(5000,80,true,7200000)==3595000,"System estimate cannot rise every refresh");
        check(e.estimate(10000,80,false,3600000)<0,"Connecting power clears previous prediction");
        System.out.println("PASS: 13 battery-estimation assertions");
    }
}
