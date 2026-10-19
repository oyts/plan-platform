package cn.zijun701.plan.mobile;

import com.getcapacitor.BridgeActivity;
import android.os.Bundle;
import android.content.Intent;

public class MainActivity extends BridgeActivity {
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);}
    @Override public void onCreate(Bundle state) {
        registerPlugin(PlanNativePlugin.class);
        super.onCreate(state);
    }
}
