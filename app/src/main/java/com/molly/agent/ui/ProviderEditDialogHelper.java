package com.molly.agent.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.molly.agent.R;
import com.molly.agent.api.MollyApiClient;
import com.molly.agent.model.ModelProviderItem;

import java.util.ArrayList;
import java.util.List;

public class ProviderEditDialogHelper {

    public interface OnProviderSavedListener {
        void onSaved(ModelProviderItem savedItem);
    }

    public static void show(Context context, MollyApiClient apiClient, ModelProviderItem existingItem, OnProviderSavedListener listener) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_edit_model_provider, null);
        dialog.setContentView(view);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int) (context.getResources().getDisplayMetrics().widthPixels * 0.94),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        TextView tvTitle = view.findViewById(R.id.tvEditProviderTitle);
        EditText etId = view.findViewById(R.id.etEditProviderId);
        EditText etDisplayName = view.findViewById(R.id.etEditProviderDisplayName);
        RadioGroup rgType = view.findViewById(R.id.rgEditProviderType);
        RadioButton rbOpenAI = view.findViewById(R.id.rbTypeOpenAI);
        RadioButton rbAnthropic = view.findViewById(R.id.rbTypeAnthropic);
        EditText etBaseUrl = view.findViewById(R.id.etEditProviderBaseUrl);
        EditText etApiKey = view.findViewById(R.id.etEditProviderApiKey);
        EditText etApiKeyEnv = view.findViewById(R.id.etEditProviderApiKeyEnv);
        CheckBox cbChat = view.findViewById(R.id.cbCapChat);
        CheckBox cbEmbedding = view.findViewById(R.id.cbCapEmbedding);
        CheckBox cbRerank = view.findViewById(R.id.cbCapRerank);
        SwitchCompat swEnabled = view.findViewById(R.id.swEditProviderEnabled);
        TextView btnCancel = view.findViewById(R.id.btnCancelEditProvider);
        TextView btnSave = view.findViewById(R.id.btnSaveEditProvider);

        final boolean isEdit = (existingItem != null);

        if (isEdit) {
            tvTitle.setText("⚙️ Konfigurasi Penyedia: " + existingItem.providerId);
            etId.setText(existingItem.providerId);
            etId.setEnabled(false);
            etId.setAlpha(0.6f);

            etDisplayName.setText(existingItem.displayName);
            if ("anthropic".equalsIgnoreCase(existingItem.providerType)) {
                rbAnthropic.setChecked(true);
            } else {
                rbOpenAI.setChecked(true);
            }
            etBaseUrl.setText(existingItem.baseUrl != null ? existingItem.baseUrl : "");
            etApiKeyEnv.setText(existingItem.apiKeyEnv != null ? existingItem.apiKeyEnv : "");

            cbChat.setChecked(existingItem.capabilities.contains("chat"));
            cbEmbedding.setChecked(existingItem.capabilities.contains("embedding"));
            cbRerank.setChecked(existingItem.capabilities.contains("rerank"));
            swEnabled.setChecked(existingItem.isEnabled);
            btnSave.setText("Perbarui Konfigurasi");
        } else {
            tvTitle.setText("➕ Tambah Penyedia Model AI");
            rbOpenAI.setChecked(true);
            swEnabled.setChecked(true);
            cbChat.setChecked(true);
            btnSave.setText("Simpan Penyedia");
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String pId = etId.getText().toString().trim().toLowerCase();
            String dName = etDisplayName.getText().toString().trim();
            String bUrl = etBaseUrl.getText().toString().trim();
            String aKey = etApiKey.getText().toString().trim();
            String aKeyEnv = etApiKeyEnv.getText().toString().trim();
            String pType = rbAnthropic.isChecked() ? "anthropic" : "openai";

            if (pId.isEmpty()) {
                etId.setError("ID Penyedia wajib diisi");
                return;
            }
            if (dName.isEmpty()) {
                etDisplayName.setError("Nama tampilan wajib diisi");
                return;
            }
            if (bUrl.isEmpty()) {
                etBaseUrl.setError("Base URL wajib diisi");
                return;
            }

            List<String> caps = new ArrayList<>();
            if (cbChat.isChecked()) caps.add("chat");
            if (cbEmbedding.isChecked()) caps.add("embedding");
            if (cbRerank.isChecked()) caps.add("rerank");
            if (caps.isEmpty()) {
                Toast.makeText(context, "Pilih minimal 1 kemampuan yang didukung", Toast.LENGTH_SHORT).show();
                return;
            }

            btnSave.setEnabled(false);
            btnSave.setText("Menyimpan...");

            JsonObject payload = new JsonObject();
            payload.addProperty("provider_id", pId);
            payload.addProperty("display_name", dName);
            payload.addProperty("provider_type", pType);
            payload.addProperty("base_url", bUrl);
            if (!aKey.isEmpty()) {
                payload.addProperty("api_key", aKey);
            }
            payload.addProperty("api_key_env", aKeyEnv);
            payload.addProperty("is_enabled", swEnabled.isChecked());

            JsonArray capArr = new JsonArray();
            for (String c : caps) capArr.add(c);
            payload.add("capabilities", capArr);

            if (isEdit) {
                // Pertahankan enabled models yang ada
                if (existingItem.enabledModels != null) {
                    JsonArray modelsArr = new JsonArray();
                    for (com.molly.agent.model.ProviderModelItem m : existingItem.enabledModels) {
                        modelsArr.add(m.toJson());
                    }
                    payload.add("enabled_models", modelsArr);
                }

                apiClient.updateModelProvider(pId, payload, new MollyApiClient.ApiCallback<ModelProviderItem>() {
                    @Override
                    public void onSuccess(ModelProviderItem result) {
                        dialog.dismiss();
                        Toast.makeText(context, "Penyedia " + dName + " berhasil diperbarui", Toast.LENGTH_SHORT).show();
                        if (listener != null) listener.onSaved(result);
                    }

                    @Override
                    public void onError(String error) {
                        btnSave.setEnabled(true);
                        btnSave.setText("Perbarui Konfigurasi");
                        Toast.makeText(context, "Gagal memperbarui: " + error, Toast.LENGTH_LONG).show();
                    }
                });
            } else {
                apiClient.createModelProvider(payload, new MollyApiClient.ApiCallback<ModelProviderItem>() {
                    @Override
                    public void onSuccess(ModelProviderItem result) {
                        dialog.dismiss();
                        Toast.makeText(context, "Penyedia " + dName + " berhasil ditambahkan", Toast.LENGTH_SHORT).show();
                        if (listener != null) listener.onSaved(result);
                    }

                    @Override
                    public void onError(String error) {
                        btnSave.setEnabled(true);
                        btnSave.setText("Simpan Penyedia");
                        Toast.makeText(context, "Gagal menambahkan: " + error, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });

        dialog.show();
    }
}
