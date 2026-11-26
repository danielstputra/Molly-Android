package com.molly.agent.intro;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
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
        private final View vAura;
        private final View vRing;
        private final ImageView ivIllustration;
        private final TextView tvTagline;
        private final TextView tvTitle;
        private final TextView tvDescription;

        private ObjectAnimator floatAnim;
        private ObjectAnimator auraScaleAnim;
        private ObjectAnimator ringRotateAnim;

        public SlideViewHolder(@NonNull View itemView) {
            super(itemView);
            vAura = itemView.findViewById(R.id.vIntroAura);
            vRing = itemView.findViewById(R.id.vIntroRing);
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

            startLiveAnimations();
        }

        private void startLiveAnimations() {
            // Cancel previous animators if recycled
            if (floatAnim != null) floatAnim.cancel();
            if (auraScaleAnim != null) auraScaleAnim.cancel();
            if (ringRotateAnim != null) ringRotateAnim.cancel();

            // 1. Continuous gentle floating bob on the icon
            ivIllustration.setTranslationY(0f);
            floatAnim = ObjectAnimator.ofFloat(ivIllustration, "translationY", -10f, 10f);
            floatAnim.setDuration(1900);
            floatAnim.setRepeatMode(ValueAnimator.REVERSE);
            floatAnim.setRepeatCount(ValueAnimator.INFINITE);
            floatAnim.setInterpolator(new AccelerateDecelerateInterpolator());
            floatAnim.start();

            // 2. Soft breathing scale on the golden background aura
            auraScaleAnim = ObjectAnimator.ofFloat(vAura, "scaleX", 0.92f, 1.08f);
            auraScaleAnim.setDuration(2400);
            auraScaleAnim.setRepeatMode(ValueAnimator.REVERSE);
            auraScaleAnim.setRepeatCount(ValueAnimator.INFINITE);
            auraScaleAnim.setInterpolator(new AccelerateDecelerateInterpolator());
            auraScaleAnim.addUpdateListener(anim -> {
                float val = (float) anim.getAnimatedValue();
                vAura.setScaleY(val);
            });
            auraScaleAnim.start();

            // 3. Subtle slow rotation of the outer ring
            ringRotateAnim = ObjectAnimator.ofFloat(vRing, "rotation", 0f, 360f);
            ringRotateAnim.setDuration(16000);
            ringRotateAnim.setRepeatCount(ValueAnimator.INFINITE);
            ringRotateAnim.setInterpolator(new android.view.animation.LinearInterpolator());
            ringRotateAnim.start();
        }
    }
}
