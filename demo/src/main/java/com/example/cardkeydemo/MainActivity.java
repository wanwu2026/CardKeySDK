package com.example.cardkeydemo;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.cardkey.CardKey;
import com.example.cardkey.core.LicensePayload;
import com.example.cardkey.ui.ActivationDialog;

/**
 * 演示接入方式。
 * 重点看 onCreate 里的三步：init 已在 Application 完成 → isActivated 判断 → 弹激活窗。
 * 界面用纯代码构建，避免额外的布局文件，方便阅读。
 */
public class MainActivity extends Activity {

    private TextView statusText;
    private TextView deviceText;
    private TextView detailText;
    private Button activateBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(24);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("CardKey 激活示例");
        title.setTextSize(22);
        root.addView(title);

        deviceText = new TextView(this);
        deviceText.setTextSize(14);
        root.addView(deviceText);

        statusText = new TextView(this);
        statusText.setTextSize(18);
        statusText.setPadding(0, dp(24), 0, 0);
        root.addView(statusText);

        detailText = new TextView(this);
        detailText.setTextSize(13);
        detailText.setPadding(0, dp(8), 0, 0);
        root.addView(detailText);

        activateBtn = new Button(this);
        activateBtn.setText("激活 / 更换卡密");
        activateBtn.setPadding(0, dp(24), 0, 0);
        root.addView(activateBtn);

        Button deactivateBtn = new Button(this);
        deactivateBtn.setText("清除激活状态（测试用）");
        root.addView(deactivateBtn);

        setContentView(root);

        deviceText.setText("设备码：" + CardKey.get().getDeviceId());
        activateBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new ActivationDialog(MainActivity.this)
                        .setOnActivatedListener(new ActivationDialog.OnActivatedListener() {
                            @Override public void onActivated(LicensePayload payload) {
                                refreshStatus();
                                Toast.makeText(MainActivity.this, "激活成功", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .show();
            }
        });
        deactivateBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                CardKey.get().deactivate();
                refreshStatus();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    /** 这就是业务侧判断"是否解锁功能"的方式。 */
    private void refreshStatus() {
        if (CardKey.get().isActivated()) {
            LicensePayload p = CardKey.get().currentLicense();
            statusText.setText("✓ 已激活");
            detailText.setText("产品：" + p.getProductId()
                    + "\n到期：" + (p.getExpiresAt() == 0 ? "永久"
                    : android.text.format.DateFormat.format("yyyy-MM-dd", p.getExpiresAt()))
                    + "\n序列号：" + p.getSerial());
        } else {
            statusText.setText("✗ 未激活");
            detailText.setText("点击「激活」输入卡密后即可解锁功能。");
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
