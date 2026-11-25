package com.molly.agent.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.molly.agent.R;
import com.molly.agent.model.ModelProviderItem;

import java.util.ArrayList;
import java.util.List;

public class ModelProvidersAdapter extends RecyclerView.Adapter<ModelProvidersAdapter.ProviderViewHolder> {

    public interface ProviderActionListener {
        void onToggleEnabled(ModelProviderItem provider, boolean enabled);
        void onManageModels(ModelProviderItem provider);
        void onConfigProvider(ModelProviderItem provider);
        void onDeleteProvider(ModelProviderItem provider);
    }

    private final Context context;
    private final List<ModelProviderItem> items = new ArrayList<>();
    private final ProviderActionListener listener;

    public ModelProvidersAdapter(Context context, ProviderActionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setItems(List<ModelProviderItem> newItems) {
        this.items.clear();
        this.items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ProviderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_model_provider, parent, false);
        return new ProviderViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ProviderViewHolder holder, int position) {
        ModelProviderItem provider = items.get(position);

        holder.tvAvatar.setText(provider.getAvatarEmoji());
        holder.tvDisplayName.setText(provider.displayName != null ? provider.displayName : provider.providerId);
        holder.tvId.setText(provider.providerId + " • " + (provider.providerType != null ? provider.providerType : "openai"));

        // Switch Enabled
        holder.swEnabled.setOnCheckedChangeListener(null);
        holder.swEnabled.setChecked(provider.isEnabled);
        holder.swEnabled.setOnCheckedChangeListener((btn, isChecked) -> {
            if (listener != null) {
                listener.onToggleEnabled(provider, isChecked);
            }
        });

        // Credential Badge
        holder.tvCredential.setText(provider.getCredentialBadgeText());
        holder.tvCredential.setTextColor(provider.getCredentialBadgeColor());
        holder.tvCredential.setBackgroundTintList(ColorStateList.valueOf(provider.getCredentialBadgeColor() & 0x33FFFFFF | 0x22000000));

        // Capabilities
        holder.tvCapabilities.setText(provider.getCapabilitiesSummary());

        // Model Count
        int modelCount = provider.getEnabledModelCount();
        holder.tvModelCount.setText(modelCount + " Model Aktif");

        // Base URL
        if (provider.baseUrl != null && !provider.baseUrl.trim().isEmpty()) {
            holder.tvBaseUrl.setVisibility(View.VISIBLE);
            holder.tvBaseUrl.setText(provider.baseUrl.trim());
        } else {
            holder.tvBaseUrl.setVisibility(View.GONE);
        }

        // Action Buttons
        holder.btnManageModels.setOnClickListener(v -> {
            if (listener != null) listener.onManageModels(provider);
        });

        holder.btnConfigProvider.setOnClickListener(v -> {
            if (listener != null) listener.onConfigProvider(provider);
        });

        holder.btnDeleteProvider.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteProvider(provider);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ProviderViewHolder extends RecyclerView.ViewHolder {
        TextView tvAvatar;
        TextView tvDisplayName;
        TextView tvId;
        SwitchCompat swEnabled;
        TextView tvCredential;
        TextView tvCapabilities;
        TextView tvModelCount;
        TextView tvBaseUrl;
        View btnManageModels;
        View btnConfigProvider;
        ImageView btnDeleteProvider;

        ProviderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAvatar = itemView.findViewById(R.id.tvProviderAvatar);
            tvDisplayName = itemView.findViewById(R.id.tvProviderDisplayName);
            tvId = itemView.findViewById(R.id.tvProviderId);
            swEnabled = itemView.findViewById(R.id.swProviderEnabled);
            tvCredential = itemView.findViewById(R.id.tvProviderCredential);
            tvCapabilities = itemView.findViewById(R.id.tvProviderCapabilities);
            tvModelCount = itemView.findViewById(R.id.tvProviderModelCount);
            tvBaseUrl = itemView.findViewById(R.id.tvProviderBaseUrl);
            btnManageModels = itemView.findViewById(R.id.btnManageModels);
            btnConfigProvider = itemView.findViewById(R.id.btnConfigProvider);
            btnDeleteProvider = itemView.findViewById(R.id.btnDeleteProvider);
        }
    }
}
