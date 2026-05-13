package com.example.cameramaltiagent.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.cameramaltiagent.BuildConfig;
import com.example.cameramaltiagent.MockAgentPipeline;
import com.example.cameramaltiagent.R;
import com.example.cameramaltiagent.agent.AgentPipeline;
import com.example.cameramaltiagent.model.AgentResult;
import com.google.gson.Gson;

import java.io.File;

/**
 * ProcessingActivity — AgentPipelineを実行してステップ進捗を表示する。
 *
 * MOCK_MODE = true のときはAPIを呼ばずダミーデータで動作確認できる。
 * APIキーを設定したら false に変更する。
 */
public class ProcessingActivity extends AppCompatActivity {

    // ★ APIキー未設定時は true にしてUIだけ確認する。キー設定後に false へ変更。
    private static final boolean MOCK_MODE = BuildConfig.GEMINI_API_KEY.isEmpty()
            || BuildConfig.GEMINI_API_KEY.equals("YOUR_GEMINI_API_KEY");

    public static final String EXTRA_CLOTHING_TEXT = "clothing_text";
    public static final String EXTRA_AGENT_RESULT = "agent_result";

    private AgentPipeline pipeline;
    private MockAgentPipeline mockPipeline;
    private TextView txtStep;
    private TextView txtStepDetail;
    private android.view.View progressBar;
    private android.view.View btnRetryBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_processing);

        txtStep = findViewById(R.id.txt_step);
        txtStepDetail = findViewById(R.id.txt_step_detail);
        progressBar = findViewById(R.id.progress_bar);
        btnRetryBack = findViewById(R.id.btn_retry_back);

        // 戻るボタン → InputActivityに戻る
        btnRetryBack.setOnClickListener(v -> finish());

        String selfiePath = getIntent().getStringExtra(CameraActivity.EXTRA_SELFIE_PATH);
        String clothingText = getIntent().getStringExtra(EXTRA_CLOTHING_TEXT);

        AgentPipeline.PipelineCallback callback = new AgentPipeline.PipelineCallback() {
            @Override
            public void onStepStarted(int step, String message) {
                String[] parts = message.split("\n", 2);
                txtStep.setText("Step " + step + "/4: " + parts[0]);
                txtStepDetail.setText(parts.length > 1 ? parts[1] : "");
                updateStepDot(step);
            }

            @Override
            public void onComplete(AgentResult result) {
                String resultJson = new Gson().toJson(result);
                Intent intent = new Intent(ProcessingActivity.this, ResultActivity.class);
                intent.putExtra(EXTRA_AGENT_RESULT, resultJson);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String errorMessage) {
                txtStep.setText("❌ エラーが発生しました");
                txtStepDetail.setText(errorMessage);
                progressBar.setVisibility(View.GONE);
                btnRetryBack.setVisibility(View.VISIBLE);
            }
        };

        if (MOCK_MODE) {
            // APIキー未設定 → モックで動作確認
            txtStepDetail.setText(" モックモードで動作中（APIキー未設定）");
            mockPipeline = new MockAgentPipeline();
            mockPipeline.execute(new File(selfiePath != null ? selfiePath : ""), clothingText, callback);
        } else {
            // 本番: 実際のAPIを呼ぶ
            pipeline = new AgentPipeline();
            pipeline.execute(new File(selfiePath), clothingText, callback);
        }
    }

    private void updateStepDot(int activeStep) {
        int[] dotIds    = {R.id.step1_dot,   R.id.step2_dot,   R.id.step3_dot,   R.id.step4_dot};
        int[] labelIds  = {R.id.step1_label, R.id.step2_label, R.id.step3_label, R.id.step4_label};

        int colorDone     = getResources().getColor(R.color.step_done,   getTheme());
        int colorActive   = getResources().getColor(R.color.step_active, getTheme());
        int colorInactive = getResources().getColor(R.color.step_inactive, getTheme());

        for (int i = 0; i < dotIds.length; i++) {
            View dot     = findViewById(dotIds[i]);
            android.widget.TextView label = findViewById(labelIds[i]);
            if (dot == null || label == null) continue;

            if (i < activeStep - 1) {
                // 完了済み: 緑ドット + 通常テキスト
                dot.setBackgroundResource(R.drawable.step_dot_inactive);
                dot.getBackground().setTint(colorDone);
                label.setTextColor(colorDone);
            } else if (i == activeStep - 1) {
                // 実行中: 黒ドット + 太字テキスト
                dot.setBackgroundResource(R.drawable.step_dot_inactive);
                dot.getBackground().setTint(colorActive);
                label.setTextColor(colorActive);
                label.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                // 未実行: グレー
                dot.setBackgroundResource(R.drawable.step_dot_inactive);
                dot.getBackground().setTint(colorInactive);
                label.setTextColor(colorInactive);
                label.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pipeline != null) pipeline.shutdown();
        if (mockPipeline != null) mockPipeline.shutdown();
    }
}
