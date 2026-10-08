#include <jni.h>
#include <string>

#include "bspatch/bspatch.h"

namespace {

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

}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_skodamusic_app_update_NativeUpdatePatcher_nativeApplyPatch(
    JNIEnv* env, jobject /*thiz*/, jstring old_apk, jstring new_apk, jstring patch_file) {
  const std::string old_path = JStringToStdString(env, old_apk);
  const std::string new_path = JStringToStdString(env, new_apk);
  const std::string patch_path = JStringToStdString(env, patch_file);

  if (old_path.empty() || new_path.empty() || patch_path.empty()) {
    return BSPATCH_ERR_OPEN_PATCH;
  }

  return static_cast<jint>(bspatch_apply(old_path.c_str(), new_path.c_str(), patch_path.c_str()));
}
