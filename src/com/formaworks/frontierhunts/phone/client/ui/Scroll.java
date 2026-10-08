package com.formaworks.frontierhunts.phone.client.ui;

/** [phone] Touch-style scrolling: drag with the mouse (with fling), or the wheel; eases to rest, rubber-bands at the ends. */
public final class Scroll {
   public float offset;
   private float target;
   private float velocity;
   public float content;
   public float view;
   private boolean dragging;
   private float lastDrag;

   public float max() {
      return Math.max(0.0F, this.content - this.view);
   }

   public void reset() {
      this.offset = 0.0F;
      this.target = 0.0F;
      this.velocity = 0.0F;
   }

   public void wheel(float notches) {
      this.target = clamp(this.target - notches * 34.0F);
      this.velocity = 0.0F;
   }

   public void dragStart() {
      this.dragging = true;
      this.velocity = 0.0F;
      this.target = this.offset;
   }

   public void drag(float dy, float dt) {
      if (!this.dragging) {
         this.dragStart();
      }
      float max = this.max();
      // resistance past the ends
      float over = this.offset < 0.0F ? -this.offset : (this.offset > max ? this.offset - max : 0.0F);
      float k = over > 0.0F ? 1.0F / (1.0F + over / 24.0F) : 1.0F;
      this.offset -= dy * k;
      this.target = this.offset;
      if (dt > 0.0F) {
         this.velocity = this.velocity * 0.6F + -dy / dt * 0.4F;
      }
      this.lastDrag = dt;
   }

   public void dragEnd() {
      this.dragging = false;
      this.target = clamp(this.offset + this.velocity * 0.22F);
      this.velocity = 0.0F;
   }

   public boolean dragging() {
      return this.dragging;
   }

   /** Advances the animation; dt in seconds. */
   public void update(float dt) {
      if (this.dragging) {
         return;
      }
      this.target = clamp(this.target);
      this.offset = Theme.approach(this.offset, this.target, 16.0F, dt);
      if (Math.abs(this.offset - this.target) < 0.05F) {
         this.offset = this.target;
      }
   }

   /** Scrolls so a span is visible. */
   public void show(float y, float h) {
      if (y < this.target) {
         this.target = clamp(y - 8.0F);
      } else if (y + h > this.target + this.view) {
         this.target = clamp(y + h + 8.0F - this.view);
      }
   }

   public void to(float y) {
      this.target = clamp(y);
   }

   private float clamp(float v) {
      return Math.max(0.0F, Math.min(this.max(), v));
   }
}
