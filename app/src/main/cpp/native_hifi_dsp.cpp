#include <jni.h>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <ctime>
#include <new>

namespace {

constexpr int kStatusOk = 0;
constexpr int kStatusBypass = 1;
constexpr int kStatusError = 2;

constexpr int kTierQuality = 0;
constexpr int kTierBalanced = 1;
constexpr int kTierSafe = 2;

constexpr int kFlagActive = 1;
constexpr int kFlagBypass = 1 << 1;
constexpr int kFlagDegraded = 1 << 2;
constexpr int kFlagOverBudget = 1 << 3;
constexpr int kFlagUnsupported = 1 << 4;
constexpr int kFlagError = 1 << 5;

constexpr int kModeOriginal = 0;
constexpr int kModeFidelity = 1;
constexpr int kModeClarity = 2;
constexpr int kModeDynamic = 3;
constexpr int kModeSoft = 4;

constexpr int kFilterPeak = 0;
constexpr int kFilterLowShelf = 1;
constexpr int kFilterHighShelf = 2;

constexpr int kMaxChannels = 2;
constexpr int kMaxFilters = 4;
constexpr float kPi = 3.14159265358979323846f;

struct FilterSpec {
  int type;
  float frequency_hz;
  float q;
  float gain_db;
};

struct ModeTierSpec {
  float preamp;
  int filter_count;
  FilterSpec filters[kMaxFilters];
};

struct Biquad {
  float b0 = 1.0f;
  float b1 = 0.0f;
  float b2 = 0.0f;
  float a1 = 0.0f;
  float a2 = 0.0f;
  float x1 = 0.0f;
  float x2 = 0.0f;
  float y1 = 0.0f;
  float y2 = 0.0f;

  void Reset() {
    x1 = 0.0f;
    x2 = 0.0f;
    y1 = 0.0f;
    y2 = 0.0f;
  }

  float Process(float input) {
    const float output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
    x2 = x1;
    x1 = input;
    y2 = y1;
    y1 = output;
    return output;
  }
};

struct ChannelState {
  Biquad filters[kMaxFilters];
};

struct DspState {
  int sample_rate = 0;
  int channel_count = 0;
  bool enabled = false;
  int mode = kModeOriginal;
  int version = -1;
  int tier = kTierQuality;
  bool bypass = false;
  int over_budget_count = 0;
  int safe_over_budget_count = 0;
  int under_budget_streak = 0;
  int error_count = 0;
  float preamp = 1.0f;
  int filter_count = 0;
  ChannelState channels[kMaxChannels];
};

int64_t NowUs() {
  timespec ts;
  if (clock_gettime(CLOCK_MONOTONIC, &ts) != 0) {
    return 0;
  }
  return static_cast<int64_t>(ts.tv_sec) * 1000000LL + ts.tv_nsec / 1000LL;
}

jlong PackResult(int status, int tier, int flags, int64_t cost_us) {
  const uint64_t safe_cost = static_cast<uint64_t>(std::max<int64_t>(0, cost_us));
  return static_cast<jlong>((safe_cost << 32) |
                            ((static_cast<uint64_t>(flags) & 0xFFFFULL) << 16) |
                            ((static_cast<uint64_t>(tier) & 0xFFULL) << 8) |
                            (static_cast<uint64_t>(status) & 0xFFULL));
}

float ClampFloat(float value, float low, float high) {
  return std::max(low, std::min(value, high));
}

int16_t ClampPcm16(int value) {
  if (value > 32767) {
    return 32767;
  }
  if (value < -32768) {
    return -32768;
  }
  return static_cast<int16_t>(value);
}

float FastLimit(float value) {
  if (value > 0.98f) {
    return 0.98f;
  }
  if (value < -0.98f) {
    return -0.98f;
  }
  if (value > 0.92f) {
    return 0.92f + (value - 0.92f) * 0.35f;
  }
  if (value < -0.92f) {
    return -0.92f + (value + 0.92f) * 0.35f;
  }
  return value;
}

void SetRaw(Biquad* target, double b0, double b1, double b2, double a0, double a1, double a2) {
  const double safe_a0 = std::fabs(a0) < 1.0e-9 ? 1.0 : a0;
  target->b0 = static_cast<float>(b0 / safe_a0);
  target->b1 = static_cast<float>(b1 / safe_a0);
  target->b2 = static_cast<float>(b2 / safe_a0);
  target->a1 = static_cast<float>(a1 / safe_a0);
  target->a2 = static_cast<float>(a2 / safe_a0);
  target->Reset();
}

Biquad CreateBiquad(const FilterSpec& spec, int sample_rate) {
  Biquad out;
  const float max_frequency = static_cast<float>(sample_rate) * 0.45f;
  const float safe_frequency = ClampFloat(spec.frequency_hz, 20.0f, max_frequency);
  const double a = std::pow(10.0, static_cast<double>(spec.gain_db) / 40.0);
  const double omega = 2.0 * static_cast<double>(kPi) * safe_frequency / sample_rate;
  const double sin_w = std::sin(omega);
  const double cos_w = std::cos(omega);
  const double q = std::max(0.1, static_cast<double>(spec.q));

  if (spec.type == kFilterPeak) {
    const double alpha = sin_w / (2.0 * q);
    SetRaw(&out,
           1.0 + alpha * a,
           -2.0 * cos_w,
           1.0 - alpha * a,
           1.0 + alpha / a,
           -2.0 * cos_w,
           1.0 - alpha / a);
    return out;
  }

  const double sqrt_a = std::sqrt(a);
  const double alpha = sin_w / 2.0 * std::sqrt(2.0);
  if (spec.type == kFilterLowShelf) {
    SetRaw(&out,
           a * ((a + 1.0) - (a - 1.0) * cos_w + 2.0 * sqrt_a * alpha),
           2.0 * a * ((a - 1.0) - (a + 1.0) * cos_w),
           a * ((a + 1.0) - (a - 1.0) * cos_w - 2.0 * sqrt_a * alpha),
           (a + 1.0) + (a - 1.0) * cos_w + 2.0 * sqrt_a * alpha,
           -2.0 * ((a - 1.0) + (a + 1.0) * cos_w),
           (a + 1.0) + (a - 1.0) * cos_w - 2.0 * sqrt_a * alpha);
  } else {
    SetRaw(&out,
           a * ((a + 1.0) + (a - 1.0) * cos_w + 2.0 * sqrt_a * alpha),
           -2.0 * a * ((a - 1.0) + (a + 1.0) * cos_w),
           a * ((a + 1.0) + (a - 1.0) * cos_w - 2.0 * sqrt_a * alpha),
           (a + 1.0) - (a - 1.0) * cos_w + 2.0 * sqrt_a * alpha,
           2.0 * ((a - 1.0) - (a + 1.0) * cos_w),
           (a + 1.0) - (a - 1.0) * cos_w - 2.0 * sqrt_a * alpha);
  }
  return out;
}

FilterSpec Peak(float frequency_hz, float q, float gain_db) {
  return {kFilterPeak, frequency_hz, q, gain_db};
}

FilterSpec LowShelf(float frequency_hz, float q, float gain_db) {
  return {kFilterLowShelf, frequency_hz, q, gain_db};
}

FilterSpec HighShelf(float frequency_hz, float q, float gain_db) {
  return {kFilterHighShelf, frequency_hz, q, gain_db};
}

ModeTierSpec SpecFor(int mode, int tier) {
  ModeTierSpec spec{};
  spec.preamp = 1.0f;
  spec.filter_count = 0;
  if (mode == kModeFidelity) {
    if (tier == kTierQuality) {
      spec.preamp = 0.86f;
      spec.filter_count = 4;
      spec.filters[0] = LowShelf(85.0f, 0.7f, 0.8f);
      spec.filters[1] = Peak(220.0f, 0.85f, -1.4f);
      spec.filters[2] = Peak(2500.0f, 0.9f, 1.1f);
      spec.filters[3] = HighShelf(9200.0f, 0.7f, 0.9f);
    } else if (tier == kTierBalanced) {
      spec.preamp = 0.88f;
      spec.filter_count = 2;
      spec.filters[0] = Peak(220.0f, 0.85f, -1.0f);
      spec.filters[1] = HighShelf(9200.0f, 0.7f, 0.8f);
    } else {
      spec.preamp = 0.9f;
      spec.filter_count = 1;
      spec.filters[0] = HighShelf(9200.0f, 0.7f, 0.6f);
    }
  } else if (mode == kModeClarity) {
    if (tier == kTierQuality) {
      spec.preamp = 0.84f;
      spec.filter_count = 3;
      spec.filters[0] = Peak(260.0f, 0.9f, -1.2f);
      spec.filters[1] = Peak(2100.0f, 0.85f, 1.8f);
      spec.filters[2] = HighShelf(8800.0f, 0.7f, 1.2f);
    } else if (tier == kTierBalanced) {
      spec.preamp = 0.86f;
      spec.filter_count = 2;
      spec.filters[0] = Peak(2100.0f, 0.85f, 1.4f);
      spec.filters[1] = HighShelf(8800.0f, 0.7f, 0.8f);
    } else {
      spec.preamp = 0.88f;
      spec.filter_count = 1;
      spec.filters[0] = Peak(2100.0f, 0.85f, 1.0f);
    }
  } else if (mode == kModeDynamic) {
    if (tier == kTierQuality) {
      spec.preamp = 0.82f;
      spec.filter_count = 3;
      spec.filters[0] = LowShelf(75.0f, 0.75f, 1.6f);
      spec.filters[1] = Peak(180.0f, 0.9f, -0.9f);
      spec.filters[2] = Peak(3600.0f, 1.0f, 0.7f);
    } else if (tier == kTierBalanced) {
      spec.preamp = 0.85f;
      spec.filter_count = 2;
      spec.filters[0] = LowShelf(75.0f, 0.75f, 1.2f);
      spec.filters[1] = Peak(3600.0f, 1.0f, 0.5f);
    } else {
      spec.preamp = 0.88f;
      spec.filter_count = 1;
      spec.filters[0] = LowShelf(75.0f, 0.75f, 0.9f);
    }
  } else if (mode == kModeSoft) {
    if (tier == kTierQuality) {
      spec.preamp = 0.9f;
      spec.filter_count = 3;
      spec.filters[0] = LowShelf(100.0f, 0.75f, 0.3f);
      spec.filters[1] = Peak(3000.0f, 0.9f, -1.1f);
      spec.filters[2] = HighShelf(7200.0f, 0.7f, -1.5f);
    } else if (tier == kTierBalanced) {
      spec.preamp = 0.92f;
      spec.filter_count = 2;
      spec.filters[0] = Peak(3000.0f, 0.9f, -0.8f);
      spec.filters[1] = HighShelf(7200.0f, 0.7f, -1.0f);
    } else {
      spec.preamp = 0.94f;
      spec.filter_count = 1;
      spec.filters[0] = HighShelf(7200.0f, 0.7f, -0.8f);
    }
  }
  return spec;
}

void ResetFilters(DspState* state) {
  if (state == nullptr) {
    return;
  }
  for (int channel = 0; channel < kMaxChannels; ++channel) {
    for (int i = 0; i < kMaxFilters; ++i) {
      state->channels[channel].filters[i].Reset();
    }
  }
}

bool RebuildFilters(DspState* state) {
  if (state == nullptr || state->sample_rate <= 0 || state->channel_count < 1 ||
      state->channel_count > kMaxChannels) {
    return false;
  }
  const ModeTierSpec spec = SpecFor(state->mode, state->tier);
  state->preamp = spec.preamp;
  state->filter_count = std::max(0, std::min(spec.filter_count, kMaxFilters));
  for (int channel = 0; channel < state->channel_count; ++channel) {
    for (int i = 0; i < state->filter_count; ++i) {
      state->channels[channel].filters[i] = CreateBiquad(spec.filters[i], state->sample_rate);
    }
    for (int i = state->filter_count; i < kMaxFilters; ++i) {
      state->channels[channel].filters[i] = Biquad{};
    }
  }
  return true;
}

DspState* FromHandle(jlong handle) {
  if (handle == 0) {
    return nullptr;
  }
  return reinterpret_cast<DspState*>(static_cast<intptr_t>(handle));
}

void CopyBytes(uint8_t* out, const uint8_t* in, int byte_count) {
  if (out == nullptr || in == nullptr || byte_count <= 0) {
    return;
  }
  std::copy(in, in + byte_count, out);
}

int64_t BudgetUs(const DspState& state, int byte_count) {
  const int frame_bytes = std::max(1, state.channel_count * 2);
  const int frames = byte_count / frame_bytes;
  const int64_t audio_us = state.sample_rate > 0
                               ? (static_cast<int64_t>(frames) * 1000000LL / state.sample_rate)
                               : 0;
  if (state.tier == kTierQuality) {
    return std::max<int64_t>(5000, audio_us / 3);
  }
  if (state.tier == kTierBalanced) {
    return std::max<int64_t>(8000, audio_us / 2);
  }
  return std::max<int64_t>(12000, (audio_us * 3) / 4);
}

int ProcessBuffer(DspState* state, const uint8_t* in, uint8_t* out, int byte_count) {
  if (state == nullptr || in == nullptr || out == nullptr || byte_count <= 0) {
    return kStatusError;
  }
  const int frame_bytes = state->channel_count * 2;
  if (!state->enabled || state->mode == kModeOriginal || state->bypass || state->sample_rate <= 0 ||
      state->channel_count < 1 || state->channel_count > kMaxChannels || frame_bytes <= 0) {
    CopyBytes(out, in, byte_count);
    return kStatusBypass;
  }

  const int frames = byte_count / frame_bytes;
  const auto* pcm_in = reinterpret_cast<const int16_t*>(in);
  auto* pcm_out = reinterpret_cast<int16_t*>(out);
  const float in_scale = state->preamp / 32768.0f;
  const int filter_count = state->filter_count;

  if (state->channel_count == 2) {
    auto& ch0 = state->channels[0];
    auto& ch1 = state->channels[1];
    for (int f = 0; f < frames; ++f) {
      float s0 = static_cast<float>(pcm_in[f * 2]) * in_scale;
      float s1 = static_cast<float>(pcm_in[f * 2 + 1]) * in_scale;
      for (int i = 0; i < filter_count; ++i) {
        s0 = ch0.filters[i].Process(s0);
        s1 = ch1.filters[i].Process(s1);
      }
      s0 = FastLimit(s0);
      s1 = FastLimit(s1);
      pcm_out[f * 2] = ClampPcm16(static_cast<int>(s0 * 32767.0f));
      pcm_out[f * 2 + 1] = ClampPcm16(static_cast<int>(s1 * 32767.0f));
    }
  } else {
    auto& ch0 = state->channels[0];
    for (int f = 0; f < frames; ++f) {
      float s = static_cast<float>(pcm_in[f]) * in_scale;
      for (int i = 0; i < filter_count; ++i) {
        s = ch0.filters[i].Process(s);
      }
      s = FastLimit(s);
      pcm_out[f] = ClampPcm16(static_cast<int>(s * 32767.0f));
    }
  }

  const int processed_bytes = frames * frame_bytes;
  if (processed_bytes < byte_count) {
    CopyBytes(out + processed_bytes, in + processed_bytes, byte_count - processed_bytes);
  }
  return kStatusOk;
}

int FlagsForStatus(int status, const DspState* state, bool over_budget, bool degraded_now) {
  int flags = 0;
  if (status == kStatusOk) {
    flags |= kFlagActive;
  } else if (status == kStatusBypass) {
    flags |= kFlagBypass;
  } else {
    flags |= kFlagError;
  }
  if (state != nullptr && state->tier != kTierQuality) {
    flags |= kFlagDegraded;
  }
  if (degraded_now) {
    flags |= kFlagDegraded;
  }
  if (over_budget) {
    flags |= kFlagOverBudget;
  }
  if (state != nullptr && state->bypass) {
    flags |= kFlagBypass;
  }
  return flags;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_skodamusic_app_audio_dsp_NativeHiFiDspBridge_nativeCreate(JNIEnv*, jobject) {
  DspState* state = new (std::nothrow) DspState();
  return reinterpret_cast<jlong>(state);
}

extern "C" JNIEXPORT void JNICALL
Java_com_skodamusic_app_audio_dsp_NativeHiFiDspBridge_nativeRelease(JNIEnv*, jobject, jlong handle) {
  DspState* state = FromHandle(handle);
  delete state;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_skodamusic_app_audio_dsp_NativeHiFiDspBridge_nativeConfigure(
    JNIEnv*, jobject, jlong handle, jint sample_rate, jint channel_count) {
  DspState* state = FromHandle(handle);
  if (state == nullptr || sample_rate <= 0 || channel_count < 1 || channel_count > kMaxChannels) {
    return kStatusError;
  }
  state->sample_rate = sample_rate;
  state->channel_count = channel_count;
  state->tier = kTierQuality;
  state->bypass = false;
  state->over_budget_count = 0;
  state->safe_over_budget_count = 0;
  state->under_budget_streak = 0;
  state->error_count = 0;
  ResetFilters(state);
  return RebuildFilters(state) ? kStatusOk : kStatusError;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_skodamusic_app_audio_dsp_NativeHiFiDspBridge_nativeSetMode(
    JNIEnv*, jobject, jlong handle, jboolean enabled, jint mode_ordinal, jint version) {
  DspState* state = FromHandle(handle);
  if (state == nullptr) {
    return kStatusError;
  }
  if (mode_ordinal < kModeOriginal || mode_ordinal > kModeSoft) {
    state->enabled = false;
    state->mode = kModeOriginal;
    state->bypass = true;
    return kStatusBypass;
  }
  const bool changed = state->enabled != (enabled == JNI_TRUE) || state->mode != mode_ordinal ||
                       state->version != version;
  state->enabled = enabled == JNI_TRUE;
  state->mode = mode_ordinal;
  state->version = version;
  if (changed) {
    state->tier = kTierQuality;
    state->bypass = false;
    state->over_budget_count = 0;
    state->safe_over_budget_count = 0;
    state->under_budget_streak = 0;
    state->error_count = 0;
  }
  if (!state->enabled || state->mode == kModeOriginal) {
    ResetFilters(state);
    return kStatusBypass;
  }
  return RebuildFilters(state) ? kStatusOk : kStatusError;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_skodamusic_app_audio_dsp_NativeHiFiDspBridge_nativeFlush(JNIEnv*, jobject, jlong handle) {
  DspState* state = FromHandle(handle);
  if (state == nullptr) {
    return kStatusError;
  }
  ResetFilters(state);
  state->bypass = false;
  state->over_budget_count = 0;
  state->safe_over_budget_count = 0;
  state->under_budget_streak = 0;
  state->error_count = 0;
  return kStatusOk;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_skodamusic_app_audio_dsp_NativeHiFiDspBridge_nativeProcessPcm16(
    JNIEnv* env, jobject, jlong handle, jobject input_buffer, jobject output_buffer, jint byte_count) {
  DspState* state = FromHandle(handle);
  if (state == nullptr || byte_count <= 0) {
    return PackResult(kStatusError, kTierSafe, kFlagError, 0);
  }

  auto* input = static_cast<uint8_t*>(env->GetDirectBufferAddress(input_buffer));
  auto* output = static_cast<uint8_t*>(env->GetDirectBufferAddress(output_buffer));
  if (input == nullptr || output == nullptr) {
    state->error_count += 1;
    return PackResult(kStatusError, state->tier, kFlagError | kFlagUnsupported, 0);
  }

  const int64_t start_us = NowUs();
  int status = ProcessBuffer(state, input, output, byte_count);
  const int64_t cost_us = NowUs() - start_us;

  if (status == kStatusError) {
    state->error_count += 1;
    if (state->error_count >= 3) {
      state->bypass = true;
      CopyBytes(output, input, byte_count);
      status = kStatusBypass;
    }
    return PackResult(status, state->tier, FlagsForStatus(status, state, false, false), cost_us);
  }

  bool degraded_now = false;
  const bool over_budget = status == kStatusOk && cost_us > BudgetUs(*state, byte_count);
  if (over_budget) {
    state->under_budget_streak = 0;
    state->over_budget_count += 1;
    if (state->tier == kTierQuality && state->over_budget_count >= 15) {
      state->tier = kTierBalanced;
      degraded_now = true;
      state->over_budget_count = 0;
      RebuildFilters(state);
    } else if (state->tier == kTierBalanced && state->over_budget_count >= 20) {
      state->tier = kTierSafe;
      degraded_now = true;
      state->over_budget_count = 0;
      RebuildFilters(state);
    } else if (state->tier == kTierSafe) {
      state->safe_over_budget_count += 1;
      if (state->safe_over_budget_count >= 30) {
        state->bypass = true;
      }
    }
  } else {
    if (state->over_budget_count > 0) {
      state->over_budget_count -= 1;
    }
    if (state->safe_over_budget_count > 0) {
      state->safe_over_budget_count -= 1;
    }
    state->under_budget_streak += 1;
    if (state->tier == kTierSafe && state->under_budget_streak >= 60) {
      state->tier = kTierBalanced;
      state->under_budget_streak = 0;
      state->safe_over_budget_count = 0;
      state->bypass = false;
      RebuildFilters(state);
    } else if (state->tier == kTierBalanced && state->under_budget_streak >= 60) {
      state->tier = kTierQuality;
      state->under_budget_streak = 0;
      state->over_budget_count = 0;
      RebuildFilters(state);
    }
  }

  int flags = FlagsForStatus(status, state, over_budget, degraded_now);
  if (state->bypass) {
    CopyBytes(output, input, byte_count);
    status = kStatusBypass;
    flags = FlagsForStatus(status, state, over_budget, degraded_now);
  }
  return PackResult(status, state->tier, flags, cost_us);
}
