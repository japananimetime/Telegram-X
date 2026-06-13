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

#include <jni.h>
#include <pthread.h>
#include <functional>
#include <memory>
#include <string>
#include <utility>

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
  struct GroupCallContext {
    std::unique_ptr<tgcalls::GroupInstanceInterface> instance;
    jobject javaInstance = nullptr;
    jclass javaClass = nullptr;
    pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;

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
  if (context->instance != nullptr) {
    context->instance->stop([] {});
  }
  delete context;
}
