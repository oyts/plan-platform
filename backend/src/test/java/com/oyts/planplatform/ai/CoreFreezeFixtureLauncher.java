package com.oyts.planplatform.ai;
/** Test-only lifecycle wrapper for the existing deterministic synthetic fixtures. */
public class CoreFreezeFixtureLauncher {
    public static void main(String[]args)throws Exception{
        if(args.length!=2)throw new IllegalArgumentException("Known fixture role and synthetic output required");
        Thread parent=new Thread(()->{try{while(System.in.read()!=-1){}System.exit(0);}catch(java.io.IOException ended){System.exit(0);}},"core-freeze-parent");parent.setDaemon(true);parent.start();
        switch(args[0]){
            case "reliability" -> AiReliabilityFixture.main(new String[]{args[1]});
            case "long" -> AiLongPlanningFixture.main(new String[]{args[1]});
            case "admin" -> AdminCenterFixture.main(new String[]{args[1]});
            case "metrics" -> com.oyts.planplatform.metrics.MetricsFixture.main(new String[]{args[1]});
            default -> throw new IllegalArgumentException("No arbitrary main classes or production arguments allowed");
        }
    }
}
