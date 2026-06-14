/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

// JNI bridge to the tgcalls GROUP engine (GroupInstanceCustomImpl), powering
// video chats / voice chats. Mirrors the 1:1 bridge in tgvoip.cpp.
//
// Slice 2a: lifecycle + the join handshake. The native instance emits a join
// payload (ssrc + JSON) that Java relays to TDLib (JoinVideoChat); TDLib's
// response payload is fed back via setJoinResponsePayload. Participant volume,
// audio-level/speaking callbacks, and the foreground-service wiring are later
// slices.

#include <jni_utils.h>
#include "bridge.h"

#include <tgcalls/group/GroupInstanceCustomImpl.h>
#include <tgcalls/StaticThreads.h>

#include <sdk/android/native_api/video/wrapper.h>

#include "video_capture_context.h"

#include <android/log.h>
#include <jni.h>
#include <pthread.h>
#include <functional>
#include <map>
#include <memory>
#include <sstream>
#include <string>
#include <utility>
#include <vector>

namespace tgcalls {
  // Defined in tgvoip.cpp: registers tgcalls + webrtc JNI and the camera classes.
  // group_call.cpp shares libtgcallsjni.so with tgvoip.cpp, so the symbol is local.
  bool initialize (JNIEnv *env);
}

namespace {
  // Captured on first newInstance() (always runs on a JNI thread). Used to attach
  // tgcalls' internal worker threads when delivering callbacks back to Java.
  JavaVM *g_vm = nullptr;

  // Attaches the current (possibly native tgcalls) thread to the JVM for the
  // duration of a callback, detaching only if we performed the attach.
  struct ScopedEnv {
    JNIEnv *env = nullptr;
    bool attached = false;

    ScopedEnv () {
      if (g_vm == nullptr) {
        return;
      }
      jint status = g_vm->GetEnv((void **) &env, JNI_VERSION_1_6);
      if (status == JNI_EDETACHED) {
        if (g_vm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
          attached = true;
        } else {
          env = nullptr;
        }
      } else if (status != JNI_OK) {
        env = nullptr;
      }
    }

    ~ScopedEnv () {
      if (attached && g_vm != nullptr) {
        g_vm->DetachCurrentThread();
      }
    }
  };

  // Owns the native group-call instance and a global ref to the Java handle for
  // the lifetime of one joined call.
  //
  // Member ORDER is load-bearing: `instance` is declared LAST so it is destroyed
  // FIRST. ~GroupInstanceCustomImpl blocks until tgcalls' worker threads drain, so
  // once `delete context` runs, no callback can fire after `instance` is gone —
  // and the mutex + Java refs the callbacks touch are still alive during that
  // drain (they're destroyed afterwards). The reverse order would tear down the
  // mutex before the drain, so a racing callback would lock a freed mutex (UAF).
  struct GroupCallContext {
    pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
    jobject javaInstance = nullptr;
    jclass javaClass = nullptr;
    // addIncomingVideoOutput() stores only a weak_ptr keyed by endpointId, so the
    // owning shared_ptr for each remote tile must outlive the instance — keep them
    // here for the lifetime of the call (cleared per-endpoint on remove, and all at
    // once when the context is destroyed, BEFORE `instance` below tears down).
    std::map<std::string, std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>> incomingVideoSinks;
    std::unique_ptr<tgcalls::GroupInstanceInterface> instance;

    // Runs act on the Java handle, guarding against teardown racing the callback.
    void callOnJava (const std::function<void(JNIEnv *, jobject, jclass)> &act) {
      pthread_mutex_lock(&mutex);
      if (javaInstance != nullptr && javaClass != nullptr) {
        ScopedEnv scoped;
        if (scoped.env != nullptr) {
          act(scoped.env, javaInstance, javaClass);
          if (scoped.env->ExceptionCheck()) {
            scoped.env->ExceptionClear();
          }
        }
      }
      pthread_mutex_unlock(&mutex);
    }
  };
}

// Creates a group-call instance in RTC mode. Returns an opaque native pointer.
JNI_OBJECT_FUNC(jlong, voip_GroupCallInstance, newInstance, jboolean muted) {
  if (g_vm == nullptr) {
    env->GetJavaVM(&g_vm);
  }

  auto *context = new GroupCallContext();
  context->javaInstance = env->NewGlobalRef(thiz);
  context->javaClass = (jclass) env->NewGlobalRef(env->GetObjectClass(thiz));

  GroupCallContext *ctx = context;

  tgcalls::GroupInstanceDescriptor descriptor;
  descriptor.threads = tgcalls::StaticThreads::getThreads();
  descriptor.isConference = false;
  descriptor.useDummyChannel = true;
  descriptor.networkStateUpdated = [ctx](tgcalls::GroupNetworkState state) {
    bool connected = state.isConnected;
    ctx->callOnJava([connected](JNIEnv *env, jobject obj, jclass cls) {
      jmethodID method = env->GetMethodID(cls, "handleNetworkStateChange", "(Z)V");
      if (method != nullptr) {
        env->CallVoidMethod(obj, method, (jboolean) connected);
      }
    });
  };

  context->instance = std::make_unique<tgcalls::GroupInstanceCustomImpl>(std::move(descriptor));
  context->instance->setConnectionMode(tgcalls::GroupConnectionMode::GroupConnectionModeRtc, true, false);
  context->instance->setIsMuted(muted == JNI_TRUE);

  return jni::ptr_to_jlong(context);
}

// Requests the tgcalls join payload; the result is delivered to Java via
// handleEmitJoinPayload(int audioSource, String json).
JNI_OBJECT_FUNC(void, voip_GroupCallInstance, emitJoinPayload, jlong ptr) {
  auto context = jni::jlong_to_ptr<GroupCallContext *>(ptr);
  if (context == nullptr || context->instance == nullptr) {
    return;
  }
  GroupCallContext *ctx = context;
  context->instance->emitJoinPayload([ctx](tgcalls::GroupJoinPayload const &payload) {
    uint32_t audioSsrc = payload.audioSsrc;
    std::string json = payload.json;
    ctx->callOnJava([audioSsrc, json](JNIEnv *env, jobject obj, jclass cls) {
      jmethodID method = env->GetMethodID(cls, "handleEmitJoinPayload", "(ILjava/lang/String;)V");
      if (method != nullptr) {
        jstring jJson = env->NewStringUTF(json.c_str());
        env->CallVoidMethod(obj, method, (jint) audioSsrc, jJson);
        env->DeleteLocalRef(jJson);
      }
    });
  });
}

// Feeds TDLib's JoinVideoChat response payload back into tgcalls.
JNI_OBJECT_FUNC(void, voip_GroupCallInstance, setJoinResponsePayload, jlong ptr, jstring jPayload) {
  auto context = jni::jlong_to_ptr<GroupCallContext *>(ptr);
  if (context == nullptr || context->instance == nullptr || jPayload == nullptr) {
    return;
  }
  std::string payload = jni::from_jstring(env, jPayload);
  context->instance->setJoinResponsePayload(payload);
}

// Adjusts the playback volume of a remote participant (0.0 .. 1.0+, 1.0 = normal).
JNI_OBJECT_FUNC(void, voip_GroupCallInstance, setVolume, jlong ptr, jint ssrc, jdouble volume) {
  auto context = jni::jlong_to_ptr<GroupCallContext *>(ptr);
  if (context != nullptr && context->instance != nullptr) {
    context->instance->setVolume((uint32_t) ssrc, (double) volume);
  }
}

// Toggles the local microphone mute state.
JNI_OBJECT_FUNC(void, voip_GroupCallInstance, setMuted, jlong ptr, jboolean muted) {
  auto context = jni::jlong_to_ptr<GroupCallContext *>(ptr);
  if (context != nullptr && context->instance != nullptr) {
    context->instance->setIsMuted(muted == JNI_TRUE);
  }
}

// ==== Video (group): outgoing camera + incoming participant tiles ====
//
// Outgoing camera: the SAME VideoCaptureContext used by 1:1 calls (created via
// voip_GroupCallInstance.nativeCreateVideoCapturer below, identical to the
// TgCallsController one) is handed to the group instance via setVideoCapture.
//
// Incoming tiles: each remote participant's video is keyed by its TDLib
// endpointId. addIncomingVideoOutput() keeps only a weak_ptr, so the owning
// shared_ptr is retained in GroupCallContext::incomingVideoSinks until the tile is
// removed (participant stopped video) or the call ends.

// Creates a camera VideoCaptureInterface (front/back). Returns a VideoCaptureContext*
// as an opaque jlong, owned by the Java side and released with
// nativeDestroyVideoCapturer. Mirrors voip_TgCallsController.nativeCreateVideoCapturer.
JNI_OBJECT_FUNC(jlong, voip_GroupCallInstance, nativeCreateVideoCapturer, jstring jDeviceId, jboolean jIsScreencast) {
  if (!tgcalls::initialize(env)) {
    return 0;
  }
  std::string deviceId = jDeviceId != nullptr ? jni::from_jstring(env, jDeviceId) : std::string();
  bool isScreencast = jIsScreencast == JNI_TRUE;

  auto *captureContext = new VideoCaptureContext();
  captureContext->platformContext = std::make_shared<tgcalls::AndroidContext>(env);
  captureContext->capture = tgcalls::VideoCaptureInterface::Create(
    tgcalls::StaticThreads::getThreads(),
    deviceId,
    isScreencast,
    captureContext->platformContext
  );
  if (captureContext->capture == nullptr) {
    delete captureContext;
    return 0;
  }
  return jni::ptr_to_jlong(captureContext);
}

// Switches the camera (front <-> back) on an existing capturer.
JNI_OBJECT_FUNC(void, voip_GroupCallInstance, nativeSwitchCamera, jlong capturePtr, jboolean jUseFrontCamera) {
  auto captureContext = jni::jlong_to_ptr<VideoCaptureContext *>(capturePtr);
  if (captureContext != nullptr && captureContext->capture != nullptr) {
    bool useFront = jUseFrontCamera == JNI_TRUE;
    captureContext->capture->switchToDevice(useFront ? "front" : "back", false);
  }
}

// Sets the capture state (0 Inactive / 1 Paused / 2 Active), matching VideoState.java.
JNI_OBJECT_FUNC(void, voip_GroupCallInstance, nativeSetVideoState, jlong capturePtr, jint jState) {
  auto captureContext = jni::jlong_to_ptr<VideoCaptureContext *>(capturePtr);
  if (captureContext != nullptr && captureContext->capture != nullptr) {
    captureContext->capture->setState(static_cast<tgcalls::VideoState>(jState));
  }
}

// Releases a capturer created by nativeCreateVideoCapturer().
JNI_OBJECT_FUNC(void, voip_GroupCallInstance, nativeDestroyVideoCapturer, jlong capturePtr) {
  auto captureContext = jni::jlong_to_ptr<VideoCaptureContext *>(capturePtr);
  delete captureContext;
}

// Routes locally-captured (preview) frames to an org.webrtc.VideoSink. setOutput
// retains the sink itself (owning shared_ptr); null clears the preview output.
JNI_OBJECT_FUNC(void, voip_GroupCallInstance, nativeSetVideoCaptureLocalOutput, jlong capturePtr, jobject jSink) {
  if (!tgcalls::initialize(env)) {
    return;
  }
  auto captureContext = jni::jlong_to_ptr<VideoCaptureContext *>(capturePtr);
  if (captureContext == nullptr || captureContext->capture == nullptr) {
    return;
  }
  if (jSink == nullptr) {
    captureContext->capture->setOutput(nullptr);
    return;
  }
  std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink =
    webrtc::JavaToNativeVideoSink(env, jSink);
  captureContext->capture->setOutput(sink);
}

// Hands a capturer (or clears it when capturePtr == 0) to the group instance.
JNI_OBJECT_FUNC(void, voip_GroupCallInstance, nativeSetVideoCapture, jlong ptr, jlong capturePtr) {
  auto context = jni::jlong_to_ptr<GroupCallContext *>(ptr);
  if (context == nullptr || context->instance == nullptr) {
    return;
  }
  if (capturePtr == 0) {
    context->instance->setVideoCapture(nullptr);
    return;
  }
  auto captureContext = jni::jlong_to_ptr<VideoCaptureContext *>(capturePtr);
  if (captureContext != nullptr) {
    context->instance->setVideoCapture(captureContext->capture);
  }
}

// Attaches an org.webrtc.VideoSink to the remote video identified by endpointId.
// The instance keeps only a weak_ptr, so the owning shared_ptr is retained in the
// per-endpoint map (replacing any previous sink for that endpoint).
JNI_OBJECT_FUNC(void, voip_GroupCallInstance, nativeAddIncomingVideoOutput, jlong ptr, jstring jEndpointId, jobject jSink) {
  if (!tgcalls::initialize(env)) {
    return;
  }
  auto context = jni::jlong_to_ptr<GroupCallContext *>(ptr);
  if (context == nullptr || context->instance == nullptr || jEndpointId == nullptr || jSink == nullptr) {
    return;
  }
  std::string endpointId = jni::from_jstring(env, jEndpointId);
  std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink =
    webrtc::JavaToNativeVideoSink(env, jSink);
  // Guard the owned-sink map with the same mutex callOnJava uses, so the
  // shared_ptr lifetime is safe against concurrent remove/stop.
  pthread_mutex_lock(&context->mutex);
  context->incomingVideoSinks[endpointId] = sink;
  pthread_mutex_unlock(&context->mutex);
  context->instance->addIncomingVideoOutput(endpointId, sink);
}

// Drops the owned sink for an endpoint (participant stopped video / tile released).
// addIncomingVideoOutput held only a weak_ptr, so releasing our shared_ptr here lets
// the instance's reference expire and stops frame delivery to the (released) renderer.
JNI_OBJECT_FUNC(void, voip_GroupCallInstance, nativeRemoveIncomingVideoOutput, jlong ptr, jstring jEndpointId) {
  auto context = jni::jlong_to_ptr<GroupCallContext *>(ptr);
  if (context == nullptr || jEndpointId == nullptr) {
    return;
  }
  std::string endpointId = jni::from_jstring(env, jEndpointId);
  pthread_mutex_lock(&context->mutex);
  context->incomingVideoSinks.erase(endpointId);
  pthread_mutex_unlock(&context->mutex);
}

// Builds std::vector<VideoChannelDescription> from parallel Java arrays and hands
// it to setRequestedVideoChannels. Each requested channel carries an endpointId, a
// quality (0 Thumbnail / 1 Medium / 2 Full, applied as both min & max), and an
// ssrc-group encoding string of the form "SEMANTICS:ssrc,ssrc;SEMANTICS:ssrc"
// (e.g. "SIM:11,22,33") matching TdApi.GroupCallVideoSourceGroup.
JNI_OBJECT_FUNC(void, voip_GroupCallInstance, nativeSetRequestedVideoChannels, jlong ptr,
                jobjectArray jEndpointIds, jintArray jQualities, jobjectArray jSsrcGroups) {
  auto context = jni::jlong_to_ptr<GroupCallContext *>(ptr);
  if (context == nullptr || context->instance == nullptr || jEndpointIds == nullptr) {
    return;
  }
  jsize count = env->GetArrayLength(jEndpointIds);

  // Validate parallel-array shape up front: when present, qualities/ssrcGroups
  // must match the endpoint count. A mismatch means a caller bug; bail rather
  // than silently using defaults for the tail.
  jsize ssrcGroupsLen = jSsrcGroups != nullptr ? env->GetArrayLength(jSsrcGroups) : 0;
  jsize qualitiesLen = jQualities != nullptr ? env->GetArrayLength(jQualities) : 0;
  if ((jQualities != nullptr && qualitiesLen != count) ||
      (jSsrcGroups != nullptr && ssrcGroupsLen != count)) {
    __android_log_print(ANDROID_LOG_ERROR, "tgx",
                        "nativeSetRequestedVideoChannels: array length mismatch (endpoints=%d qualities=%d ssrcGroups=%d)",
                        (int) count, (int) qualitiesLen, (int) ssrcGroupsLen);
    return;
  }

  std::vector<tgcalls::VideoChannelDescription> channels;
  channels.reserve(count);

  jint *qualities = jQualities != nullptr ? env->GetIntArrayElements(jQualities, nullptr) : nullptr;

  for (jsize i = 0; i < count; i++) {
    auto jEndpointId = (jstring) env->GetObjectArrayElement(jEndpointIds, i);
    if (jEndpointId == nullptr) {
      continue;
    }
    tgcalls::VideoChannelDescription channel;
    channel.endpointId = jni::from_jstring(env, jEndpointId);
    env->DeleteLocalRef(jEndpointId);

    tgcalls::VideoChannelDescription::Quality quality = tgcalls::VideoChannelDescription::Quality::Medium;
    if (qualities != nullptr && i < qualitiesLen) {
      switch (qualities[i]) {
        case 0: quality = tgcalls::VideoChannelDescription::Quality::Thumbnail; break;
        case 2: quality = tgcalls::VideoChannelDescription::Quality::Full; break;
        default: quality = tgcalls::VideoChannelDescription::Quality::Medium; break;
      }
    }
    channel.minQuality = tgcalls::VideoChannelDescription::Quality::Thumbnail;
    channel.maxQuality = quality;

    if (jSsrcGroups != nullptr && i < ssrcGroupsLen) {
      auto jGroups = (jstring) env->GetObjectArrayElement(jSsrcGroups, i);
      if (jGroups != nullptr) {
        std::string encoded = jni::from_jstring(env, jGroups);
        env->DeleteLocalRef(jGroups);
        // Parse "SEMANTICS:ssrc,ssrc;SEMANTICS:ssrc".
        std::stringstream groupStream(encoded);
        std::string groupToken;
        while (std::getline(groupStream, groupToken, ';')) {
          if (groupToken.empty()) {
            continue;
          }
          auto colon = groupToken.find(':');
          if (colon == std::string::npos) {
            continue;
          }
          tgcalls::MediaSsrcGroup group;
          group.semantics = groupToken.substr(0, colon);
          std::stringstream ssrcStream(groupToken.substr(colon + 1));
          std::string ssrcToken;
          while (std::getline(ssrcStream, ssrcToken, ',')) {
            if (ssrcToken.empty()) {
              continue;
            }
            try {
              group.ssrcs.push_back((uint32_t) std::stoul(ssrcToken));
            } catch (...) {
              // Skip malformed ssrc token.
            }
          }
          if (!group.ssrcs.empty()) {
            channel.ssrcGroups.push_back(std::move(group));
          }
        }
      }
    }
    channels.push_back(std::move(channel));
  }

  if (qualities != nullptr) {
    env->ReleaseIntArrayElements(jQualities, qualities, JNI_ABORT);
  }

  context->instance->setRequestedVideoChannels(std::move(channels));
}

// Stops the instance and releases all native resources.
JNI_OBJECT_FUNC(void, voip_GroupCallInstance, stopNative, jlong ptr) {
  auto context = jni::jlong_to_ptr<GroupCallContext *>(ptr);
  if (context == nullptr) {
    return;
  }
  // Drop the Java refs under the mutex first, so no in-flight callback touches a
  // half-destroyed handle.
  pthread_mutex_lock(&context->mutex);
  jobject javaInstance = context->javaInstance;
  jclass javaClass = context->javaClass;
  context->javaInstance = nullptr;
  context->javaClass = nullptr;
  pthread_mutex_unlock(&context->mutex);
  if (javaInstance != nullptr) {
    env->DeleteGlobalRef(javaInstance);
  }
  if (javaClass != nullptr) {
    env->DeleteGlobalRef(javaClass);
  }
  // Drop the owned incoming sinks under the mutex before the instance teardown,
  // so any in-flight add/remove on another thread sees a consistent map.
  pthread_mutex_lock(&context->mutex);
  context->incomingVideoSinks.clear();
  pthread_mutex_unlock(&context->mutex);
  if (context->instance != nullptr) {
    context->instance->stop([] {});
  }
  delete context;
}
