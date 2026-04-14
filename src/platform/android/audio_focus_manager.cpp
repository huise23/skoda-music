#include "src/platform/android/audio_focus_manager.h"

#include <utility>

namespace skodamusic::platform::android {
namespace {

constexpr int kAudioFocusGain = 1;
constexpr int kAudioFocusLoss = -1;
constexpr int kAudioFocusLossTransient = -2;
constexpr int kAudioFocusLossTransientCanDuck = -3;

constexpr char kHintRequestFocus[] = "应用进入前台，开始请求音频焦点";
constexpr char kHintBackgroundAbandon[] = "应用进入后台，释放音频焦点并暂停";
constexpr char kHintFocusGained[] = "获得音频焦点，恢复播放";
constexpr char kHintFocusLost[] = "失去音频焦点，暂停播放";
constexpr char kHintFocusTransient[] = "焦点暂时丢失，暂停播放";
constexpr char kHintFocusDuck[] = "焦点可鸭叫，降低音量";
constexpr char kHintFocusUnknown[] = "未知焦点事件，保持当前状态";

}  // namespace

AudioFocusManager::AudioFocusManager() {
  snapshot_.lifecycle_state = AppLifecycleState::kForeground;
  snapshot_.focus_state = AudioFocusState::kNone;
  snapshot_.should_play = false;
  snapshot_.is_ducking = false;
}

void AudioFocusManager::SetActionCallback(
    std::function<void(AudioFocusAction)> callback) {
  action_callback_ = std::move(callback);
}

AudioFocusSnapshot AudioFocusManager::OnEnterForeground() {
  snapshot_.lifecycle_state = AppLifecycleState::kForeground;
  snapshot_.focus_state = AudioFocusState::kRequested;
  snapshot_.is_ducking = false;
  snapshot_.should_play = false;
  return EmitAction(AudioFocusAction::kRequestFocus, kHintRequestFocus);
}

AudioFocusSnapshot AudioFocusManager::OnEnterBackground() {
  snapshot_.lifecycle_state = AppLifecycleState::kBackground;
  snapshot_.focus_state = AudioFocusState::kNone;
  snapshot_.should_play = false;
  snapshot_.is_ducking = false;
  EmitAction(AudioFocusAction::kPausePlayback, kHintBackgroundAbandon);
  return EmitAction(AudioFocusAction::kAbandonFocus, kHintBackgroundAbandon);
}

AudioFocusSnapshot AudioFocusManager::OnFocusChanged(int android_focus_change_code) {
  if (android_focus_change_code == kAudioFocusGain) {
    snapshot_.focus_state = AudioFocusState::kGained;
    snapshot_.is_ducking = false;
    snapshot_.should_play = snapshot_.lifecycle_state == AppLifecycleState::kForeground;
    EmitAction(AudioFocusAction::kUnduckPlayback, kHintFocusGained);
    return EmitAction(AudioFocusAction::kResumePlayback, kHintFocusGained);
  }

  if (android_focus_change_code == kAudioFocusLoss) {
    snapshot_.focus_state = AudioFocusState::kLost;
    snapshot_.should_play = false;
    snapshot_.is_ducking = false;
    return EmitAction(AudioFocusAction::kPausePlayback, kHintFocusLost);
  }

  if (android_focus_change_code == kAudioFocusLossTransient) {
    snapshot_.focus_state = AudioFocusState::kTransientLoss;
    snapshot_.should_play = false;
    snapshot_.is_ducking = false;
    return EmitAction(AudioFocusAction::kPausePlayback, kHintFocusTransient);
  }

  if (android_focus_change_code == kAudioFocusLossTransientCanDuck) {
    snapshot_.focus_state = AudioFocusState::kCanDuck;
    snapshot_.should_play = true;
    snapshot_.is_ducking = true;
    return EmitAction(AudioFocusAction::kDuckPlayback, kHintFocusDuck);
  }

  return EmitAction(AudioFocusAction::kNone, kHintFocusUnknown);
}

AudioFocusSnapshot AudioFocusManager::Snapshot() const {
  return snapshot_;
}

AudioFocusSnapshot AudioFocusManager::EmitAction(AudioFocusAction action,
                                                 const std::string& hint) {
  snapshot_.hint = hint;
  if (action_callback_) {
    action_callback_(action);
  }
  return snapshot_;
}

}  // namespace skodamusic::platform::android

