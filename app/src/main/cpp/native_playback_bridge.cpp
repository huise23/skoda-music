#include <jni.h>

#include <mutex>
#include <string>
#include <vector>

#include "src/playback/playback_queue.h"

namespace {

std::mutex g_mu;
skodamusic::playback::PlaybackQueue g_queue;

std::string JStringToStdString(JNIEnv* env, jstring value) {
  if (value == nullptr) {
    return {};
  }
  const char* chars = env->GetStringUTFChars(value, nullptr);
  if (chars == nullptr) {
    return {};
  }
  std::string out(chars);
  env->ReleaseStringUTFChars(value, chars);
  return out;
}

jstring CurrentTitleLocked(JNIEnv* env) {
  const auto snapshot = g_queue.Snapshot();
  if (!snapshot.has_track) {
    return nullptr;
  }
  return env->NewStringUTF(snapshot.current_track.title.c_str());
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_skodamusic_app_NativePlaybackBridge_nativeInitQueue(
    JNIEnv* env, jobject /*thiz*/, jobjectArray track_titles) {
  if (track_titles == nullptr) {
    return JNI_FALSE;
  }

  const jsize count = env->GetArrayLength(track_titles);
  std::vector<skodamusic::playback::QueueTrack> tracks;
  tracks.reserve(static_cast<size_t>(count));

  for (jsize i = 0; i < count; ++i) {
    auto* title = static_cast<jstring>(env->GetObjectArrayElement(track_titles, i));
    const std::string title_text = JStringToStdString(env, title);
    env->DeleteLocalRef(title);
    if (title_text.empty()) {
      continue;
    }

    skodamusic::playback::QueueTrack track;
    track.track_id = "track-" + std::to_string(static_cast<int>(i));
    track.title = title_text;
    track.artist = "";
    tracks.push_back(track);
  }

  std::lock_guard<std::mutex> lock(g_mu);
  g_queue.SetQueue(tracks, 0);
  return g_queue.Snapshot().has_track ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_skodamusic_app_NativePlaybackBridge_nativeCurrentTitle(
    JNIEnv* env, jobject /*thiz*/) {
  std::lock_guard<std::mutex> lock(g_mu);
  return CurrentTitleLocked(env);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_skodamusic_app_NativePlaybackBridge_nativeMoveNextAndGetTitle(
    JNIEnv* env, jobject /*thiz*/) {
  std::lock_guard<std::mutex> lock(g_mu);
  if (!g_queue.MoveNext()) {
    return nullptr;
  }
  return CurrentTitleLocked(env);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_skodamusic_app_NativePlaybackBridge_nativeHasNext(
    JNIEnv* env, jobject /*thiz*/) {
  std::lock_guard<std::mutex> lock(g_mu);
  const auto snapshot = g_queue.Snapshot();
  if (!snapshot.has_track) {
    return JNI_FALSE;
  }
  const int max_index = static_cast<int>(snapshot.queue.size()) - 1;
  return snapshot.current_index < max_index ? JNI_TRUE : JNI_FALSE;
}
