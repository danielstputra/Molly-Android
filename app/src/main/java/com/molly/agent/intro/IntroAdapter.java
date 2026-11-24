package com.molly.agent.intro;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.molly.agent.R;

import java.util.List;

public class IntroAdapter extends RecyclerView.Adapter<IntroAdapter.SlideViewHolder> {

    private final List<IntroSlide> slides;

    public IntroAdapter(List<IntroSlide> slides) {
        this.slides = slides;
    }

    @NonNull
    @Override
    public SlideViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_intro_slide, parent, false);
        return new SlideViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SlideViewHolder holder, int position) {
        IntroSlide slide = slides.get(position);
        holder.bind(slide);
    }

    @Override
    public int getItemCount() {
        return slides.size();
    }

    static class SlideViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivIllustration;
        private final TextView tvTagline;
        private final TextView tvTitle;
        private final TextView tvDescription;

        public SlideViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIllustration = itemView.findViewById(R.id.ivIntroIllustration);
            tvTagline = itemView.findViewById(R.id.tvIntroTagline);
            tvTitle = itemView.findViewById(R.id.tvIntroTitle);
            tvDescription = itemView.findViewById(R.id.tvIntroDescription);
        }

        public void bind(IntroSlide slide) {
            ivIllustration.setImageResource(slide.getIllustrationRes());
            tvTagline.setText(slide.getTagline());
            tvTitle.setText(slide.getTitle());
            tvDescription.setText(slide.getDescription());
        }
    }
}
