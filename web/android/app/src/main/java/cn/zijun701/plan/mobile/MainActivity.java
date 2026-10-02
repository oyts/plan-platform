package cn.zijun701.plan.mobile;

import com.getcapacitor.BridgeActivity;
import android.os.Bundle;

public class MainActivity extends BridgeActivity {
    @Override public void onCreate(Bundle state) {
        registerPlugin(PlanNativePlugin.class);
        super.onCreate(state);
    }
}
