package net.openrs.cache.skeleton.rt7_anims;


import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class AnimationKeyFrame {
   boolean is_disabled;
   boolean interpolate_timing;
   boolean dirty = true;
   boolean is_constant;
   InterpolationType end_interpolation_type;
   InterpolationType start_interpolation_type;
   AnimKey[] keyframes;
   float start;
   float end;
   float minimum_value;
   float maximum_value;
   float[] interpolated_time_buffer = new float[4];
   float[] interpolated_value_buffer = new float[4];
   float[] values;
   int cached_keyframe_id = 0;
   int start_tick;
   int end_tick;

   public AnimationKeyFrame() {
   }

   public int deserialise(ByteBuffer packet, int version) {
      int mutations_count = packet.getShort() & 0xFFFF;
      int test = packet.get() & 0xFF;
      this.start_interpolation_type = InterpolationType.lookup_by_id(packet.get() & 0xFF);
      this.end_interpolation_type = InterpolationType.lookup_by_id(packet.get() & 0xFF);
      this.interpolate_timing = (packet.get() & 0xFF) != 0;
      this.keyframes = new AnimKey[mutations_count];
      AnimKey last_keyframe = null;


      for(int index = 0; index < mutations_count; ++index) {
         AnimKey keyframe = new AnimKey();
         keyframe.deserialise(packet, version);
         this.keyframes[index] = keyframe;
         if (null != last_keyframe) {
            last_keyframe.next = keyframe;
         }

         last_keyframe = keyframe;
      }

      this.start_tick = this.keyframes[0].tick;
      this.end_tick = this.keyframes[this.get_keyframes_count() - 1].tick;
      this.values = new float[this.get_duration() + 1];

      for(int tick = this.get_start_tick(); tick <= this.get_end_tick(); ++tick) {
         this.values[tick - this.get_start_tick()] = calculate_interpolated_value(this, (float)tick);
      }

      this.keyframes = null;
      this.minimum_value = calculate_interpolated_value(this, (float)(this.get_start_tick() - 1));
      this.maximum_value = calculate_interpolated_value(this, (float)(this.get_end_tick() + 1));
      return mutations_count;
   }

   public float get_value(int tick) {
      if (tick < this.get_start_tick()) {
         return this.minimum_value;
      } else {
         return tick > this.get_end_tick() ? this.maximum_value : this.values[tick - this.get_start_tick()];
      }
   }
   public void encode(DataOutputStream dos) throws IOException {
      dos.writeBoolean(is_disabled);
      dos.writeBoolean(interpolate_timing);
      dos.writeBoolean(is_constant);
      dos.writeUTF(start_interpolation_type.toString());  // Assuming InterpolationType has a name that can be serialized
      dos.writeUTF(end_interpolation_type.toString());
      dos.writeInt(start_tick);
      dos.writeInt(end_tick);
      dos.writeFloat(start);
      dos.writeFloat(end);
      dos.writeFloat(minimum_value);
      dos.writeFloat(maximum_value);

      // Handling array of interpolated_time_buffer
      for (float value : interpolated_time_buffer) {
         dos.writeFloat(value);
      }

      // Handling array of interpolated_value_buffer
      for (float value : interpolated_value_buffer) {
         dos.writeFloat(value);
      }

      // Handling keyframes array
      if (keyframes != null) {
         dos.writeInt(keyframes.length);  // Write the length of the keyframes array
         for (AnimKey keyframe : keyframes) {
            keyframe.encode(dos);  // Assuming AnimKey has an encode method
         }
      } else {
         dos.writeInt(0);  // No keyframes available
      }
   }
   int get_start_tick() {
      return this.start_tick;
   }

   int get_end_tick() {
      return this.end_tick;
   }

   int get_duration() {
      return this.get_end_tick() - this.get_start_tick();
   }

   int get_keyframe_by_tick(float tick) {
      if (this.cached_keyframe_id >= 0 && (float)this.keyframes[this.cached_keyframe_id].tick <= tick && (null == this.keyframes[this.cached_keyframe_id].next || (float)this.keyframes[this.cached_keyframe_id].next.tick > tick)) {
         return this.cached_keyframe_id;
      } else if (!(tick < (float)this.get_start_tick()) && !(tick > (float)this.get_end_tick())) {
         int count = this.get_keyframes_count();
         int selected = this.cached_keyframe_id;
         if (count > 0) {
            int end = 0;
            int start = count - 1;

            do {
               int middle = start + end >> 1;
               if (tick < (float)this.keyframes[middle].tick) {
                  if (tick > (float)this.keyframes[middle - 1].tick) {
                     selected = middle - 1;
                     break;
                  }

                  start = middle - 1;
               } else {
                  if (!(tick > (float)this.keyframes[middle].tick)) {
                     selected = middle;
                     break;
                  }

                  if (tick < (float)this.keyframes[middle + 1].tick) {
                     selected = middle;
                     break;
                  }

                  end = middle + 1;
               }
            } while(end <= start);
         }

         if (selected != this.cached_keyframe_id) {
            this.cached_keyframe_id = selected;
            this.dirty = true;
         }

         return this.cached_keyframe_id;
      } else {
         return -1;
      }
   }

   AnimKey get_keyframe(float value) {
      int index = this.get_keyframe_by_tick(value);
      return index >= 0 && index < this.keyframes.length ? this.keyframes[index] : null;
   }

   int get_keyframes_count() {
      return this.keyframes == null ? 0 : this.keyframes.length;
   }

   static float calculate_interpolated_value(AnimationKeyFrame operation, float tick) {
      if (operation == null || operation.get_keyframes_count() == 0) {
         return 0.0F;
      }
      if (tick < (float) operation.keyframes[0].tick) {
         return InterpolationType.STEP_INTERPOLATION == operation.start_interpolation_type ? operation.keyframes[0].value : calculate_at_point(operation, tick, true);
      }
      if (tick > (float) operation.keyframes[operation.get_keyframes_count() - 1].tick) {
         return operation.end_interpolation_type == InterpolationType.STEP_INTERPOLATION ? operation.keyframes[operation.get_keyframes_count() - 1].value : calculate_at_point(operation, tick, false);
      }
      if (operation.is_disabled) {
         return operation.keyframes[0].value;
      }
      AnimKey var3 = operation.get_keyframe(tick);
      boolean has_no_next = false;
      boolean has_default_next = false;
      if (null == var3) {
         return 0.0F;
      }

      if ((double) var3.end_val1 == 0.0D && 0.0D == (double) var3.end_val2) {
         has_no_next = true;
      } else if (Float.MAX_VALUE == var3.end_val1 && Float.MAX_VALUE == var3.end_val2) {
         has_default_next = true;
      } else if (var3.next == null) {
         has_no_next = true;
      } else if (operation.dirty) {
         float[] time_markers = new float[4];
         float[] value_markers = new float[4];
         time_markers[0] = (float) var3.tick;
         value_markers[0] = var3.value;
         time_markers[1] = time_markers[0] + 0.33333334F * var3.end_val1;
         value_markers[1] = 0.33333334F * var3.end_val2 + value_markers[0];
         time_markers[3] = (float) var3.next.tick;
         value_markers[3] = var3.next.value;
         time_markers[2] = time_markers[3] - var3.next.start_val1 * 0.33333334F;
         value_markers[2] = value_markers[3] - var3.next.start_val2 * 0.33333334F;
         if (operation.interpolate_timing) {
            calc_cubicspline_timing_coefficients(operation, time_markers, value_markers);
         } else {
            calc_cubicspline_shape_coefficients(operation, time_markers, value_markers);
         }

         operation.dirty = false;
      }

      if (has_no_next) {
         return var3.value;
      } else if (has_default_next) {
         return (float) var3.tick != tick && var3.next != null ? var3.next.value : var3.value;
      } else {
         return operation.interpolate_timing ? interpolate_timing(operation, tick) : interpolate_shape(operation, tick);
      }
   }

   /**
    * Adjusts the cubic spline coefficients timing/speed
    */
   private static void calc_cubicspline_timing_coefficients(AnimationKeyFrame operation, float[] time_markers, float[] value_markers) {
      if (null != operation) {
         float trans_duration = time_markers[3] - time_markers[0];
         if ((double) trans_duration != 0.0D) {
            float start_time_delta = time_markers[1] - time_markers[0];
            float finish_time_delta = time_markers[2] - time_markers[0];
            Float start_point = start_time_delta / trans_duration;
            Float finish_point = finish_time_delta / trans_duration;
            operation.is_constant = start_point == 0.33333334F && finish_point == 0.6666667F;
            float original_start_point = start_point;
            float original_finish_point = finish_point;
            if ((double) start_point < 0.0D) {
               start_point = 0.0F;
            }

            if ((double) finish_point > 1.0D) {
               finish_point = 1.0F;
            }

            if ((double) start_point > 1.0D || finish_point < -1.0F) {
               Float clamped_start_point = start_point;
               Float clamped_finish_point = 1.0F - finish_point;
               if (start_point < 0.0F) {
                  clamped_start_point = 0.0F;
               }

               if (clamped_finish_point < 0.0F) {
                  clamped_finish_point = 0.0F;
               }

               if (clamped_start_point > 1.0F || clamped_finish_point > 1.0F) {
                  float clamped_duration = (float) ((double) (clamped_start_point * (clamped_start_point - 2.0F + clamped_finish_point)) + (double) clamped_finish_point * ((double) clamped_finish_point - 2.0D) + 1.0D);
                  if (AnimationConstants.INTERPOLATION_EPSILON + clamped_duration > 0.0F) {
                     float[] clamped_points = clamp_extra(clamped_start_point, clamped_finish_point);
                     clamped_start_point = clamped_points[0];
                     clamped_finish_point = clamped_points[1];
                  }
               }

               clamped_finish_point = 1.0F - clamped_finish_point;

               start_point = clamped_start_point;
               finish_point = clamped_finish_point;
            }

            if (start_point != original_start_point) {
               time_markers[1] = time_markers[0] + start_point * trans_duration;
               if (0.0D != (double) original_start_point) {
                  value_markers[1] = value_markers[0] + (value_markers[1] - value_markers[0]) * start_point / original_start_point;
               }
            }

            if (finish_point != original_finish_point) {
               time_markers[2] = time_markers[0] + finish_point * trans_duration;
               if ((double) original_finish_point != 1.0D) {
                  value_markers[2] = (float) ((double) value_markers[3] - (double) (value_markers[3] - value_markers[2]) * (1.0D - (double) finish_point) / (1.0D - (double) original_finish_point));
               }
            }

            operation.start = time_markers[0];
            operation.end = time_markers[3];
            float start_interpolate = start_point;
            float finish_interpolate = finish_point;
            float[] time_buffer = operation.interpolated_time_buffer;
            float time_before_interpolation = start_interpolate - 0.0F;
            float time_of_interpolation = finish_interpolate - start_interpolate;
            float time_after_interpolation = 1.0F - finish_interpolate;
            float time_misalignment = time_of_interpolation - time_before_interpolation;
            time_buffer[3] = time_after_interpolation - time_of_interpolation - time_misalignment;
            time_buffer[2] = time_misalignment + time_misalignment + time_misalignment;
            time_buffer[1] = time_before_interpolation + time_before_interpolation + time_before_interpolation;
            time_buffer[0] = 0.0F;
            float start_value = value_markers[0];
            float start_value_interpolate = value_markers[1];
            float finish_value_interpolate = value_markers[2];
            float finish_value = value_markers[3];
            float[] value_buffer = operation.interpolated_value_buffer;
            float value_before_interpolation = start_value_interpolate - start_value;
            float value_of_interpolation = finish_value_interpolate - start_value_interpolate;
            float value_after_interpolation = finish_value - finish_value_interpolate;
            float value_misalignment = value_of_interpolation - value_before_interpolation;
            value_buffer[3] = value_after_interpolation - value_of_interpolation - value_misalignment;
            value_buffer[2] = value_misalignment + value_misalignment + value_misalignment;
            value_buffer[1] = value_before_interpolation + value_before_interpolation + value_before_interpolation;
            value_buffer[0] = start_value;
         }
      }
   }

   static float calculate_at_point(AnimationKeyFrame operation, float current_tick, boolean forward) {
      if (operation == null || operation.get_keyframes_count() == 0) {
         return 0.0F;
      }
      float start_tick = (float)operation.keyframes[0].tick;
      float end_tick = (float)operation.keyframes[operation.get_keyframes_count() - 1].tick;
      float duration = end_tick - start_tick;
      if ((double)duration == 0.0D) {
         return operation.keyframes[0].value;
      } else {
         float percentage_elapsed = 0.0F;
         if (current_tick > end_tick) {
            percentage_elapsed = (current_tick - end_tick) / duration;
         } else {
            percentage_elapsed = (current_tick - start_tick) / duration;
         }

         double elapsed_integer = (int)percentage_elapsed;
         float elapsed_diff_fraction = Math.abs((float)((double)percentage_elapsed - elapsed_integer));
         float result_tick = duration * elapsed_diff_fraction;
         double elapsed_double = Math.abs(elapsed_integer + 1.0D);
         double half_elapsed = elapsed_double / 2.0D;
         double half_elapsed_floored = (int)half_elapsed;
         float elapsed = (float)(half_elapsed - half_elapsed_floored);
         if (forward) {
            if (operation.start_interpolation_type == InterpolationType.FORWARDS_INTERPOLATION) {
               if ((double) elapsed == 0.0D) {
                  result_tick = end_tick - result_tick;
               } else {
                  result_tick += start_tick;
               }
            } else if (InterpolationType.BACKWARDS_INTERPOLATION == operation.start_interpolation_type || InterpolationType.CUBICSPLINE_INTERPOLATION == operation.start_interpolation_type) {
               result_tick = end_tick - result_tick;
            } else if (operation.start_interpolation_type == InterpolationType.LINEAR_INTERPOLATION) {
               result_tick = start_tick - current_tick;
               float end_time = operation.keyframes[0].start_val1;
               float end_value = operation.keyframes[0].start_val2;
               float next_val = operation.keyframes[0].value;
               if ((double) end_time != 0.0D) {
                  next_val -= result_tick * end_value / end_time;
               }

               return next_val;
            }
         } else if (InterpolationType.FORWARDS_INTERPOLATION == operation.end_interpolation_type) {
            if ((double) elapsed == 0.0D) {
               result_tick += start_tick;
            } else {
               result_tick = end_tick - result_tick;
            }
         } else if (InterpolationType.BACKWARDS_INTERPOLATION == operation.end_interpolation_type || InterpolationType.CUBICSPLINE_INTERPOLATION == operation.end_interpolation_type) {
            result_tick += start_tick;
         } else if (InterpolationType.LINEAR_INTERPOLATION == operation.end_interpolation_type) {
            result_tick = current_tick - end_tick;
            float begin_time = operation.keyframes[operation.get_keyframes_count() - 1].end_val1;
            float begin_value = operation.keyframes[operation.get_keyframes_count() - 1].end_val2;
            float next_value = operation.keyframes[operation.get_keyframes_count() - 1].value;
            if (0.0D != (double) begin_time) {
               next_value += result_tick * begin_value / begin_time;
            }

            return next_value;
         }

         float p = calculate_interpolated_value(operation, result_tick);
         if (forward && InterpolationType.CUBICSPLINE_INTERPOLATION == operation.start_interpolation_type) {
            float var19 = operation.keyframes[operation.get_keyframes_count() - 1].value - operation.keyframes[0].value;
            p = (float)((double)p - (double)var19 * elapsed_double);
         } else if (!forward && InterpolationType.CUBICSPLINE_INTERPOLATION == operation.end_interpolation_type) {
            float var19 = operation.keyframes[operation.get_keyframes_count() - 1].value - operation.keyframes[0].value;
            p = (float)((double)p + elapsed_double * (double)var19);
         }

         return p;
      }

   }

   /**
    * Adjusts the cubic spline coefficients to change the shape of the spline
    */
   static void calc_cubicspline_shape_coefficients(AnimationKeyFrame operation, float[] time, float[] values) {
      if (operation != null) {
         operation.start = time[0];
         float total_time = time[3] - time[0];
         float total_change = values[3] - values[0];
         float time_to_begin_or_finish = time[1] - time[0];
         float in_tangent = 0.0F;
         float out_tangent = 0.0F;
         if ((double)time_to_begin_or_finish != 0.0D) {
            in_tangent = (values[1] - values[0]) / time_to_begin_or_finish;
         }

         time_to_begin_or_finish = time[3] - time[2];
         if (0.0D != (double)time_to_begin_or_finish) {
            out_tangent = (values[3] - values[2]) / time_to_begin_or_finish;
         }

         float inv_time_sq = 1.0F / (total_time * total_time);
         float total_change_b = total_time * in_tangent;
         float total_change_c = out_tangent * total_time;
         operation.interpolated_time_buffer[0] = inv_time_sq * (total_change_c + total_change_b - total_change - total_change) / total_time;
         operation.interpolated_time_buffer[1] = inv_time_sq * (total_change + total_change + total_change - total_change_b - total_change_b - total_change_c);
         operation.interpolated_time_buffer[2] = in_tangent;
         operation.interpolated_time_buffer[3] = values[0];
      }
   }

   static float interpolate_shape(AnimationKeyFrame operation, float tick) {
      if (operation == null) {
         return 0.0F;
      } else {
         float elapsed = tick - operation.start;
         return operation.interpolated_time_buffer[3] + (operation.interpolated_time_buffer[2] + (elapsed * operation.interpolated_time_buffer[0] + operation.interpolated_time_buffer[1]) * elapsed) * elapsed;
      }
   }

   static float interpolate_timing(AnimationKeyFrame operation, float tick) {
      if (operation == null) {
         return 0.0F;
      }
      float elapsed;
      if (operation.start == tick) {
         elapsed = 0.0F;
      } else if (operation.end == tick) {
         elapsed = 1.0F;
      } else {
         elapsed = (tick - operation.start) / (operation.end - operation.start);
      }

      float var4;
      if (operation.is_constant) {
         var4 = elapsed;
      } else {
         float[] input = new float[]{operation.interpolated_time_buffer[0] - elapsed, operation.interpolated_time_buffer[1], operation.interpolated_time_buffer[2], operation.interpolated_time_buffer[3]};
         float[] result = new float[5];
         int var7 = linear_interpolate(input, 3, 0.0F, true, 1.0F, true, result);
         if (var7 == 1) {
            var4 = result[0];
         } else {
            var4 = 0.0F;
         }
      }

      return var4 * (operation.interpolated_value_buffer[1] + var4 * (operation.interpolated_value_buffer[2] + var4 * operation.interpolated_value_buffer[3])) + operation.interpolated_value_buffer[0];
   }

   public static int linear_interpolate(float[] keyframe_values, int keyframe_count, float start_tick, boolean clampstart, float end_tick, boolean clampend, float[] interpolated_values) {
      float keyframe_sum = 0.0F;

      for(int var9 = 0; var9 < 1 + keyframe_count; ++var9) {
         keyframe_sum += Math.abs(keyframe_values[var9]);
      }

      float compare_threshold = (Math.abs(start_tick) + Math.abs(end_tick)) * (float)(keyframe_count + 1) * AnimationConstants.INTERPOLATION_EPSILON;
      if (keyframe_sum <= compare_threshold) {
         return -1;
      } else {
         float[] weights = new float[keyframe_count + 1];

         int interpolated_idx;
         for(interpolated_idx = 0; interpolated_idx < keyframe_count + 1; ++interpolated_idx) {
            weights[interpolated_idx] = keyframe_values[interpolated_idx] * (1.0F / keyframe_sum);
         }

         while(Math.abs(weights[keyframe_count]) < compare_threshold) {
            --keyframe_count;
         }

         interpolated_idx = 0;
         if (keyframe_count == 0) {
            return interpolated_idx;
         } else if (keyframe_count == 1) {
            interpolated_values[0] = -weights[0] / weights[1];
            boolean is_start_range = clampstart ? start_tick < interpolated_values[0] + compare_threshold : start_tick < interpolated_values[0] - compare_threshold;
            boolean is_end_range = clampend ? end_tick > interpolated_values[0] - compare_threshold : end_tick > interpolated_values[0] + compare_threshold;
            interpolated_idx = is_start_range && is_end_range ? 1 : 0;
            if (interpolated_idx > 0) {
               if (clampstart && interpolated_values[0] < start_tick) {
                  interpolated_values[0] = start_tick;
               } else if (clampend && interpolated_values[0] > end_tick) {
                  interpolated_values[0] = end_tick;
               }
            }

            return interpolated_idx;
         } else {
            InterpolationChain weight_table = new InterpolationChain(weights, keyframe_count);
            float[] weighted_keyframes = InterpolationChain.scale_weights(keyframe_count, weights);

            float[] interpolated_ticks = new float[keyframe_count + 1];
            int cur_interpolated_idx = linear_interpolate(weighted_keyframes, keyframe_count - 1, start_tick, false, end_tick, false, interpolated_ticks);
            if (cur_interpolated_idx == -1) {
               return 0;
            } else {
               boolean skip_point = false;
               float next_weight = 0.0F;
               float start_weight = 0.0F;
               float next_val = 0.0F;

               for(int idx = 0; idx <= cur_interpolated_idx; ++idx) {
                  if (interpolated_idx > keyframe_count) {
                     return interpolated_idx;
                  }

                  float current_tick;
                  if (idx == 0) {
                     current_tick = start_tick;
                     start_weight = InterpolationChain.calc_cumulative_weight(weights, keyframe_count, start_tick);
                     if (Math.abs(start_weight) <= compare_threshold && clampstart) {
                        interpolated_values[interpolated_idx++] = start_tick;
                     }
                  } else {
                     current_tick = next_val;
                     start_weight = next_weight;
                  }

                  if (idx == cur_interpolated_idx) {
                     next_val = end_tick;
                     skip_point = false;
                  } else {
                     next_val = interpolated_ticks[idx];
                  }

                  next_weight = InterpolationChain.calc_cumulative_weight(weights, keyframe_count, next_val);
                  if (skip_point) {
                     skip_point = false;
                  } else if (Math.abs(next_weight) < compare_threshold) {
                     if (idx != cur_interpolated_idx || clampend) {
                        interpolated_values[interpolated_idx++] = next_val;
                        skip_point = true;
                     }
                  } else if (start_weight < 0.0F && next_weight > 0.0F || start_weight > 0.0F && next_weight < 0.0F) {
                     interpolated_values[interpolated_idx++] = bisect_interpolation_root(weight_table, current_tick, next_val, 0.0F);
                     // smooth tangents
                     if (interpolated_idx > 1 && interpolated_values[interpolated_idx - 2] >= interpolated_values[interpolated_idx - 1] - compare_threshold) {
                        interpolated_values[interpolated_idx - 2] = 0.5F * (interpolated_values[interpolated_idx - 1] + interpolated_values[interpolated_idx - 2]);
                        --interpolated_idx;
                     }
                  }
               }

               return interpolated_idx;
            }
         }
      }
   }

   public static float bisect_interpolation_root(InterpolationChain weight_table, float start_tick, float end_tick, float tolerance) {
      float start_weight = InterpolationChain.calc_cumulative_weight(weight_table.weights, weight_table.interpolation_count, start_tick);
      if (Math.abs(start_weight) < AnimationConstants.INTERPOLATION_EPSILON) {
         return start_tick;
      }
      float end_weight = InterpolationChain.calc_cumulative_weight(weight_table.weights, weight_table.interpolation_count, end_tick);
      if (Math.abs(end_weight) < AnimationConstants.INTERPOLATION_EPSILON) {
         return end_tick;
      }
      float current_tick = 0.0F;
      float next_step = 0.0F;
      float previous_step = 0.0F;
      float current_weight = 0.0F;
      boolean is_first_iteration = true;
      boolean is_searching = false;

      do {
         is_searching = false;
         if (is_first_iteration) {
            current_tick = start_tick;
            current_weight = start_weight;
            next_step = end_tick - start_tick;
            previous_step = next_step;
            is_first_iteration = false;
         }

         if (Math.abs(current_weight) < Math.abs(end_weight)) {
            start_tick = end_tick;
            end_tick = current_tick;
            current_tick = start_tick;
            start_weight = end_weight;
            end_weight = current_weight;
            current_weight = start_weight;
         }

         float step_threshold = AnimationConstants.FLOAT_INTERPOLATION_THRESHOLD * Math.abs(end_tick) + tolerance * 0.5F;
         float half_step_dist = 0.5F * (current_tick - end_tick);
         boolean continues = Math.abs(half_step_dist) > step_threshold && 0.0F != end_weight;

         if (continues) {
            if ((Math.abs(previous_step) < step_threshold) || (Math.abs(start_weight) <= Math.abs(end_weight))) {
               next_step = half_step_dist;
               previous_step = half_step_dist;
            } else {
               float weight_ratio = end_weight / start_weight;
               float step;
               float var11;
               if (current_tick == start_tick) {
                  step = half_step_dist * 2.0F * weight_ratio;
                  var11 = 1.0F - weight_ratio;
               } else {
                  var11 = start_weight / current_weight;
                  float var12 = end_weight / current_weight;
                  step = weight_ratio * (var11 * half_step_dist * 2.0F * (var11 - var12) - (end_tick - start_tick) * (var12 - 1.0F));
                  var11 = (weight_ratio - 1.0F) * (var12 - 1.0F) * (var11 - 1.0F);
               }

               if ((double)step > 0.0D) {
                  var11 = -var11;
               } else {
                  step = -step;
               }

               weight_ratio = previous_step;
               previous_step = next_step;
               if (2.0F * step < var11 * half_step_dist * 3.0F - Math.abs(step_threshold * var11) && step < Math.abs(var11 * weight_ratio * 0.5F)) {
                  next_step = step / var11;
               } else {
                  next_step = half_step_dist;
                  previous_step = half_step_dist;
               }
            }

            start_tick = end_tick;
            start_weight = end_weight;
            if (Math.abs(next_step) > step_threshold) {
               end_tick += next_step;
            } else if ((double)half_step_dist > 0.0D) {
               end_tick += step_threshold;
            } else {
               end_tick -= step_threshold;
            }

            end_weight = InterpolationChain.calc_cumulative_weight(weight_table.weights, weight_table.interpolation_count, end_tick);
            if ((double)(end_weight * (current_weight / Math.abs(current_weight))) > 0.0D) {
               is_first_iteration = true;
               is_searching = true;
            } else {
               is_searching = true;
            }
         }
      } while(is_searching);

      return end_tick;
   }

   public static float[] clamp_extra(float tick_start, float tick_finish) {
      if (tick_start + AnimationConstants.INTERPOLATION_EPSILON < 1.3333334F) {
         float x = tick_start - 2.0F;
         float y = tick_start - 1.0F;
         float var5 = (float)Math.sqrt(x * x - y * y * 4.0F);
         float halfstep = 0.5F * (var5 + -x);
         if (tick_finish + AnimationConstants.INTERPOLATION_EPSILON > halfstep) {
            tick_finish = halfstep - AnimationConstants.INTERPOLATION_EPSILON;
         } else {
            halfstep = 0.5F * (-x - var5);
            if (tick_finish < halfstep + AnimationConstants.INTERPOLATION_EPSILON) {
               tick_finish = AnimationConstants.INTERPOLATION_EPSILON + halfstep;
            }
         }
      } else {
         tick_start = 1.3333334F - AnimationConstants.INTERPOLATION_EPSILON;
         tick_finish = 0.33333334F - AnimationConstants.INTERPOLATION_EPSILON;
      }
      return new float[] {tick_start, tick_finish};
   }
}
