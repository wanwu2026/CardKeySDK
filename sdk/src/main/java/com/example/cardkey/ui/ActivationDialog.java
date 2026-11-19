package com.example.cardkey.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.cardkey.CardKey;
import com.example.cardkey.R;
import com.example.cardkey.core.LicenseException;
import com.example.cardkey.core.LicensePayload;

/**
 * 通用激活对话框。开发者一行代码即可弹出：
 *   new ActivationDialog(context).show();
 *
 * 它调用 CardKey 单例完成激活；成功后可自动关闭并回调。
 */
public class ActivationDialog extends Dialog {
    private EditText codeInput;
    private TextView message;
    private ProgressBar progress;
    private Button btnActivate;
    private OnActivatedListener listener;

    public interface OnActivatedListener {
        void onActivated(LicensePayload payload);
    }

    public ActivationDialog(Context context) {
        super(context);
    }

    public ActivationDialog setOnActivatedListener(OnActivatedListener l) {
        this.listener = l;
        return this;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ck_dialog_activation);
        setTitle("激活 " + CardKey.get().getProductId());

        codeInput = findViewById(R.id.ck_code_input);
        message = findViewById(R.id.ck_message);
        progress = findViewById(R.id.ck_progress);
        btnActivate = findViewById(R.id.ck_btn_activate);
        Button btnCancel = findViewById(R.id.ck_btn_cancel);

        TextView deviceView = findViewById(R.id.ck_device_id);
        deviceView.setText("设备码：" + CardKey.get().getDeviceId());

        btnActivate.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String code = codeInput.getText().toString().trim();
                if (code.length() == 0) {
                    message.setText("请输入卡密");
                    return;
                }
                setLoading(true);
                CardKey.get().activate(code, new CardKey.Callback() {
                    @Override public void onSuccess(LicensePayload payload) {
                        setLoading(false);
                        message.setText("激活成功，有效期：" + (payload.getExpiresAt() == 0 ? "永久" : android.text.format.DateFormat.format("yyyy-MM-dd", payload.getExpiresAt())));
                        dismiss();
                        if (listener != null) listener.onActivated(payload);
                    }
                    @Override public void onError(LicenseException error) {
                        setLoading(false);
                        message.setText(error.friendlyMessage());
                    }
                });
            }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dismiss(); }
        });
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnActivate.setEnabled(!loading);
        codeInput.setEnabled(!loading);
    }
}
