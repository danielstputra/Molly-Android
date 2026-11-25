package com.molly.agent.ui;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;

import com.google.gson.JsonObject;
import com.molly.agent.R;
import com.molly.agent.api.MollyApiClient;
import com.molly.agent.model.ScheduledTaskItem;

import java.util.List;
import java.util.Locale;

public class ScheduledTaskEditDialogHelper {

    public interface OnTaskSavedListener {
        void onSaved();
    }

    public static void show(Context context, MollyApiClient apiClient, ScheduledTaskItem existingTask, OnTaskSavedListener savedListener) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_edit_scheduled_task, null);

        TextView tvTitle = view.findViewById(R.id.tvEditTaskDialogTitle);
        EditText etName = view.findViewById(R.id.etEditTaskName);
        TextView tvAgentSelect = view.findViewById(R.id.tvEditTaskAgentSelect);
        EditText etPrompt = view.findViewById(R.id.etEditTaskPrompt);

        RadioGroup rgScheduleType = view.findViewById(R.id.rgScheduleType);
        RadioButton rbDaily = view.findViewById(R.id.rbDaily);
        RadioButton rbWeekly = view.findViewById(R.id.rbWeekly);
        RadioButton rbMonthly = view.findViewById(R.id.rbMonthly);
        RadioButton rbCustomCron = view.findViewById(R.id.rbCustomCron);

        View llTimeContainer = view.findViewById(R.id.llTimePickerContainer);
        TextView tvExecutionTime = view.findViewById(R.id.tvExecutionTime);
        View llWeeklyContainer = view.findViewById(R.id.llWeeklyDayContainer);
        TextView tvWeeklyDaySelect = view.findViewById(R.id.tvWeeklyDaySelect);
        View llMonthlyContainer = view.findViewById(R.id.llMonthlyDayContainer);
        EditText etMonthlyDay = view.findViewById(R.id.etMonthlyDay);
        View llCustomCronContainer = view.findViewById(R.id.llCustomCronContainer);
        EditText etCustomCron = view.findViewById(R.id.etCustomCron);
        TextView tvCronPreview = view.findViewById(R.id.tvCronSummaryPreview);

        RadioGroup rgApproval = view.findViewById(R.id.rgToolApproval);
        RadioButton rbApprovalTrust = view.findViewById(R.id.rbApprovalTrust);
        RadioButton rbApprovalManual = view.findViewById(R.id.rbApprovalManual);
        SwitchCompat swEnabled = view.findViewById(R.id.swEditTaskEnabled);

        TextView btnCancel = view.findViewById(R.id.btnCancelEditTask);
        TextView btnSave = view.findViewById(R.id.btnSaveTask);

        // State holder
        final boolean isEditMode = (existingTask != null);
        final int[] hourHolder = new int[]{9};
        final int[] minuteHolder = new int[]{0};
        final int[] weekdayHolder = new int[]{1}; // 1 = Senin
        final String[] selectedAgentSlug = new String[]{""};
        final String[] selectedAgentName = new String[]{""};

        tvTitle.setText(isEditMode ? "✏️ Edit Tugas Terjadwal" : "⏰ Buat Tugas Terjadwal");
        btnSave.setText(isEditMode ? "Simpan Perubahan" : "Buat Jadwal");

        // Daftar Agen dari Cache/Server
        List<MollyApiClient.AgentItem> availableAgents = apiClient.getCachedOrFallbackAgents();
        if (!availableAgents.isEmpty()) {
            selectedAgentSlug[0] = availableAgents.get(0).id;
            selectedAgentName[0] = availableAgents.get(0).name;
            tvAgentSelect.setText("🤖 " + selectedAgentName[0]);
        }

        // Parsing data eksisting jika Edit Mode
        if (isEditMode) {
            etName.setText(existingTask.name != null ? existingTask.name : "");
            etPrompt.setText(existingTask.prompt != null ? existingTask.prompt : "");
            swEnabled.setChecked(existingTask.enabled);

            if (existingTask.agentSlug != null && !existingTask.agentSlug.isEmpty()) {
                selectedAgentSlug[0] = existingTask.agentSlug;
                for (MollyApiClient.AgentItem ag : availableAgents) {
                    if (ag.id.equalsIgnoreCase(existingTask.agentSlug)) {
                        selectedAgentName[0] = ag.name;
                        break;
                    }
                }
                tvAgentSelect.setText("🤖 " + (selectedAgentName[0].isEmpty() ? existingTask.agentSlug : selectedAgentName[0]));
            }

            if ("default".equalsIgnoreCase(existingTask.toolApprovalMode) || "manual".equalsIgnoreCase(existingTask.toolApprovalMode)) {
                rbApprovalManual.setChecked(true);
            } else {
                rbApprovalTrust.setChecked(true);
            }

            // Parse cron jika ada
            if (existingTask.cronExpression != null && !existingTask.cronExpression.isEmpty()) {
                String[] parts = existingTask.cronExpression.trim().split("\\s+");
                if (parts.length == 5) {
                    try {
                        minuteHolder[0] = Integer.parseInt(parts[0]);
                        hourHolder[0] = Integer.parseInt(parts[1]);
                    } catch (Exception ignored) {}

                    if (parts[2].equals("*") && parts[3].equals("*") && parts[4].equals("*")) {
                        rbDaily.setChecked(true);
                    } else if (parts[2].equals("*") && parts[3].equals("*") && !parts[4].equals("*")) {
                        rbWeekly.setChecked(true);
                        try {
                            weekdayHolder[0] = Integer.parseInt(parts[4]);
                        } catch (Exception ignored) {}
                    } else if (!parts[2].equals("*") && parts[3].equals("*") && parts[4].equals("*")) {
                        rbMonthly.setChecked(true);
                        etMonthlyDay.setText(parts[2]);
                    } else {
                        rbCustomCron.setChecked(true);
                        etCustomCron.setText(existingTask.cronExpression);
                    }
                } else {
                    rbCustomCron.setChecked(true);
                    etCustomCron.setText(existingTask.cronExpression);
                }
            }
        }

        // Function helper untuk update tampilan jam dan preview cron
        Runnable updateCronPreview = () -> {
            String timeFormatted = String.format(Locale.US, "%02d:%02d WIB", hourHolder[0], minuteHolder[0]);
            tvExecutionTime.setText(timeFormatted);

            String cron;
            String desc;
            if (rbDaily.isChecked()) {
                cron = String.format(Locale.US, "%d %d * * *", minuteHolder[0], hourHolder[0]);
                desc = "Setiap hari pukul " + String.format(Locale.US, "%02d:%02d", hourHolder[0], minuteHolder[0]);
            } else if (rbWeekly.isChecked()) {
                cron = String.format(Locale.US, "%d %d * * %d", minuteHolder[0], hourHolder[0], weekdayHolder[0]);
                desc = "Setiap " + getWeekdayName(weekdayHolder[0]) + " pukul " + String.format(Locale.US, "%02d:%02d", hourHolder[0], minuteHolder[0]);
            } else if (rbMonthly.isChecked()) {
                String dayStr = etMonthlyDay.getText().toString().trim();
                int day = 1;
                try { day = Math.max(1, Math.min(31, Integer.parseInt(dayStr))); } catch (Exception ignored) {}
                cron = String.format(Locale.US, "%d %d %d * *", minuteHolder[0], hourHolder[0], day);
                desc = "Tanggal " + day + " setiap bulan pukul " + String.format(Locale.US, "%02d:%02d", hourHolder[0], minuteHolder[0]);
            } else {
                cron = etCustomCron.getText().toString().trim();
                if (cron.isEmpty()) cron = "0 9 * * *";
                desc = "Ekspresi Cron kustom";
            }
            tvCronPreview.setText("Ringkasan: " + desc + " (" + cron + ")");
        };

        // Radio group listener
        rgScheduleType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbDaily) {
                llTimeContainer.setVisibility(View.VISIBLE);
                llWeeklyContainer.setVisibility(View.GONE);
                llMonthlyContainer.setVisibility(View.GONE);
                llCustomCronContainer.setVisibility(View.GONE);
            } else if (checkedId == R.id.rbWeekly) {
                llTimeContainer.setVisibility(View.VISIBLE);
                llWeeklyContainer.setVisibility(View.VISIBLE);
                llMonthlyContainer.setVisibility(View.GONE);
                llCustomCronContainer.setVisibility(View.GONE);
            } else if (checkedId == R.id.rbMonthly) {
                llTimeContainer.setVisibility(View.VISIBLE);
                llWeeklyContainer.setVisibility(View.GONE);
                llMonthlyContainer.setVisibility(View.VISIBLE);
                llCustomCronContainer.setVisibility(View.GONE);
            } else {
                llTimeContainer.setVisibility(View.GONE);
                llWeeklyContainer.setVisibility(View.GONE);
                llMonthlyContainer.setVisibility(View.GONE);
                llCustomCronContainer.setVisibility(View.VISIBLE);
            }
            updateCronPreview.run();
        });

        // Klik jam eksekusi -> TimePickerDialog
        tvExecutionTime.setOnClickListener(v -> {
            new TimePickerDialog(context, (tpView, hourOfDay, minute) -> {
                hourHolder[0] = hourOfDay;
                minuteHolder[0] = minute;
                updateCronPreview.run();
            }, hourHolder[0], minuteHolder[0], true).show();
        });

        // Klik hari mingguan -> Dialog pilihan hari
        String[] days = new String[]{"Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu"};
        int[] cronDays = new int[]{1, 2, 3, 4, 5, 6, 7};
        tvWeeklyDaySelect.setOnClickListener(v -> {
            new AlertDialog.Builder(context)
                    .setTitle("Pilih Hari Eksekusi")
                    .setItems(days, (d, which) -> {
                        weekdayHolder[0] = cronDays[which];
                        tvWeeklyDaySelect.setText(days[which] + " ❯");
                        updateCronPreview.run();
                    })
                    .show();
        });

        // Agen Selector dialog
        tvAgentSelect.setOnClickListener(v -> {
            if (availableAgents.isEmpty()) {
                Toast.makeText(context, "Daftar agen belum dimuat dari server", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] names = new String[availableAgents.size()];
            for (int i = 0; i < availableAgents.size(); i++) {
                names[i] = availableAgents.get(i).name;
            }
            new AlertDialog.Builder(context)
                    .setTitle("Pilih Agen Pelaksana")
                    .setItems(names, (d, which) -> {
                        selectedAgentSlug[0] = availableAgents.get(which).id;
                        selectedAgentName[0] = availableAgents.get(which).name;
                        tvAgentSelect.setText("🤖 " + selectedAgentName[0]);
                    })
                    .show();
        });

        updateCronPreview.run();

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String prompt = etPrompt.getText().toString().trim();
            if (name.isEmpty()) {
                etName.setError("Nama tugas wajib diisi");
                return;
            }
            if (selectedAgentSlug[0].isEmpty()) {
                Toast.makeText(context, "Pilih agen pelaksana terlebih dahulu", Toast.LENGTH_SHORT).show();
                return;
            }
            if (prompt.isEmpty()) {
                etPrompt.setError("Instruksi tugas (prompt) wajib diisi");
                return;
            }

            // Hitung ekspresi cron
            String finalCron;
            if (rbDaily.isChecked()) {
                finalCron = String.format(Locale.US, "%d %d * * *", minuteHolder[0], hourHolder[0]);
            } else if (rbWeekly.isChecked()) {
                finalCron = String.format(Locale.US, "%d %d * * %d", minuteHolder[0], hourHolder[0], weekdayHolder[0]);
            } else if (rbMonthly.isChecked()) {
                String dayStr = etMonthlyDay.getText().toString().trim();
                int day = 1;
                try { day = Math.max(1, Math.min(31, Integer.parseInt(dayStr))); } catch (Exception ignored) {}
                finalCron = String.format(Locale.US, "%d %d %d * *", minuteHolder[0], hourHolder[0], day);
            } else {
                finalCron = etCustomCron.getText().toString().trim();
                if (finalCron.isEmpty()) finalCron = "0 9 * * *";
            }

            String approvalMode = rbApprovalManual.isChecked() ? "default" : "always_trust";
            boolean isEnabled = swEnabled.isChecked();

            JsonObject payload = new JsonObject();
            payload.addProperty("name", name);
            payload.addProperty("agent_slug", selectedAgentSlug[0]);
            payload.addProperty("prompt", prompt);
            payload.addProperty("cron_expression", finalCron);
            payload.addProperty("timezone", "Asia/Jakarta");
            payload.addProperty("tool_approval_mode", approvalMode);
            payload.addProperty("enabled", isEnabled);

            btnSave.setEnabled(false);
            btnSave.setText("Menyimpan...");

            if (isEditMode) {
                apiClient.updateScheduledTask(existingTask.id, payload, new MollyApiClient.ApiCallback<ScheduledTaskItem>() {
                    @Override
                    public void onSuccess(ScheduledTaskItem result) {
                        dialog.dismiss();
                        Toast.makeText(context, "Tugas terjadwal berhasil diperbarui", Toast.LENGTH_SHORT).show();
                        if (savedListener != null) savedListener.onSaved();
                    }

                    @Override
                    public void onError(String error) {
                        btnSave.setEnabled(true);
                        btnSave.setText("Simpan Perubahan");
                        Toast.makeText(context, "Gagal memperbarui: " + error, Toast.LENGTH_LONG).show();
                    }
                });
            } else {
                apiClient.createScheduledTask(payload, new MollyApiClient.ApiCallback<ScheduledTaskItem>() {
                    @Override
                    public void onSuccess(ScheduledTaskItem result) {
                        dialog.dismiss();
                        Toast.makeText(context, "Tugas terjadwal baru berhasil dibuat!", Toast.LENGTH_SHORT).show();
                        if (savedListener != null) savedListener.onSaved();
                    }

                    @Override
                    public void onError(String error) {
                        btnSave.setEnabled(true);
                        btnSave.setText("Buat Jadwal");
                        Toast.makeText(context, "Gagal membuat tugas: " + error, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });

        dialog.show();
    }

    private static String getWeekdayName(int dow) {
        switch (dow) {
            case 1: return "Senin";
            case 2: return "Selasa";
            case 3: return "Rabu";
            case 4: return "Kamis";
            case 5: return "Jumat";
            case 6: return "Sabtu";
            case 7: return "Minggu";
            default: return "Hari " + dow;
        }
    }
}
