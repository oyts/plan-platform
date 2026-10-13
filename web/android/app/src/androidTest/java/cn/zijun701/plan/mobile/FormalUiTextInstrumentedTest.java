package cn.zijun701.plan.mobile;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.UiAutomation;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Separate test APK. Only synthetic Todo/search text in the real formal app.
 * Never reads session stores, fills login fields or changes debug/security flags.
 */
@RunWith(AndroidJUnit4.class)
public class FormalUiTextInstrumentedTest {
    private static final String FORMAL = "cn.zijun701.plan.app";
    private UiAutomation automation;
    private List<AccessibilityNodeInfo> nodes() {
        List<AccessibilityNodeInfo> result = new ArrayList<>();
        for (var window : automation.getWindows()) {
            var root = window.getRoot();
            if (window.isActive() && root != null && root.getPackageName()!=null && FORMAL.contentEquals(root.getPackageName())) flatten(root, result);
        }
        assertFalse("Unlock and foreground the formal app before testing", result.isEmpty());
        for (var node : result) assertFalse("Authentication input is never automated", node.isPassword());
        return result;
    }
    private void flatten(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> result) {
        result.add(node);
        for (int i=0; i<node.getChildCount(); i++) { var child=node.getChild(i); if(child!=null)flatten(child,result); }
    }
    private AccessibilityNodeInfo input(String label) {
        var nodes=nodes();
        List<Integer> labels=new ArrayList<>();
        for(int i=0;i<nodes.size();i++) {
            var value=nodes.get(i).getText();
            if(value!=null && label.contentEquals(value))labels.add(i);
        }
        assertEquals("Expected one observed field label",1,labels.size());
        int next=labels.get(0)+1;
        assertTrue("Observed field has no adjacent input",next<nodes.size());
        var field=nodes.get(next);
        assertEquals("Only observed text inputs are editable","android.widget.EditText",String.valueOf(field.getClassName()));
        assertFalse("Password input forbidden",field.isPassword());
        return field;
    }
    @Test public void setSyntheticFieldExactly() {
        var keyguard=(android.app.KeyguardManager)InstrumentationRegistry.getInstrumentation().getContext().getSystemService(android.content.Context.KEYGUARD_SERVICE);
        assertFalse("Unlock the phone before UI automation",keyguard.isKeyguardLocked());
        var arguments=InstrumentationRegistry.getArguments();
        String selector=arguments.getString("field","search"), text=arguments.getString("text","");
        assertTrue("Only Todo title/search fields allowed",selector.equals("search")||selector.equals("title"));
        String label=selector.equals("search")?"搜索":"待办标题";
        assertTrue("Only synthetic text or empty search allowed",text.matches("nativeupgrade-[a-z0-9-]{1,100}")||(label.equals("搜索")&&text.isEmpty()));
        automation=InstrumentationRegistry.getInstrumentation().getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        var info=automation.getServiceInfo();info.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;automation.setServiceInfo(info);
        var field=input(label);
        var existing=field.getText();
        String before=existing==null?"":existing.toString();
        if(label.equals("待办标题"))assertTrue("Real existing titles cannot be edited",before.isEmpty()||before.startsWith("nativeupgrade-"));
        var value=new Bundle();value.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);
        assertTrue("Native accessibility text action failed",field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,value));
        boolean exact=false;
        for(int attempt=0;attempt<30;attempt++) {
            SystemClock.sleep(100);
            var observed=input(label).getText();
            if(text.equals(observed==null?"":observed.toString())){exact=true;break;}
        }
        assertTrue("Exact synthetic value was not verified; draft must not be saved",exact);
    }
}
